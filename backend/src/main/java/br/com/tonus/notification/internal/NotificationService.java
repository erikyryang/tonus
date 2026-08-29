package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.DeliveryStatus;
import br.com.tonus.notification.internal.domain.MessageTemplate;
import br.com.tonus.notification.internal.domain.NotificationLog;
import br.com.tonus.notification.internal.domain.NotificationLogRepository;
import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentContact;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifies a student, exactly once.
 *
 * Runs in its own transaction (REQUIRES_NEW) because a single event can turn
 * into N notices: on a holiday, each student needs to commit on their own.
 * Everything in a single commit would mean that failing on student 150
 * undoes the 149 rows already recorded and reprocessing resends 149
 * messages — partial failure turning into mass duplication, which is
 * exactly the worst possible side effect here.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RegistrationFacade registration;
    private final NotificationLogRepository logs;
    private final WhatsAppSender sender;
    private final Clock clock;

    NotificationService(RegistrationFacade registration, NotificationLogRepository logs,
                        WhatsAppSender sender, Clock clock) {
        this.registration = registration;
        this.logs = logs;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Save first, send afterward. The INSERT has to reach the database
     * before the external call: it's the unique constraint, not the query
     * above it, that holds back two concurrent deliveries of the same
     * event — between querying and sending there's a window, and the
     * registry delivers at-least-once.
     *
     * If the send fails, the exception rolls back this transaction, the row
     * disappears, and the publication stays incomplete to be reprocessed.
     *
     * In the race — two deliveries of the same event passing the query
     * together — the losing INSERT violates the constraint and this
     * exception surfaces too. That's the correct behavior, and it's
     * intentionally not handled here: once the constraint is violated the
     * transaction is already doomed, so swallowing the error would just
     * swap the rollback for an UnexpectedRollbackException on commit.
     * Reprocessing solves it, and on the second pass the query finds the
     * row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStudent(Long studentId, MessageTemplate template,
                              String referenceKey, Object... args) {
        if (logs.existsByTemplateAndStudentIdAndReferenceKey(template, studentId, referenceKey)) {
            return;
        }

        // Whoever left the studio receives nothing more — not a schedule
        // notice, which makes no sense for someone with no class, nor a
        // billing one, because chasing a former student's debt is a decision
        // for the admin, not a side effect of a nightly job. The same
        // asymmetry as the rest of the project applies: automation serves to
        // stop, not to keep going.
        //
        // studentIsActive also covers the student who never existed. In
        // both cases nothing is thrown: reprocessing wouldn't bring the
        // student back, and the publication would stay pending forever,
        // clogging up the registry.
        //
        // The skip is recorded, not just logged: the DISCARDED row is what
        // keeps a redelivery of the same event — a fan-out that failed on
        // another student keeps the publication incomplete — from sending a
        // stale notice to a student who was reactivated in the meantime.
        if (!registration.studentIsActive(studentId)) {
            log.info("Aviso {} descartado: aluno {} não está ativo no cadastro", template, studentId);
            markDiscarded(studentId, template, referenceKey);
            return;
        }

        Optional<StudentContact> contact = registration.findStudentContact(studentId);
        if (contact.isEmpty()) {
            log.warn("Aviso {} descartado: aluno {} sumiu do cadastro", template, studentId);
            markDiscarded(studentId, template, referenceKey);
            return;
        }

        logs.saveAndFlush(new NotificationLog(studentId, template, referenceKey, clock.instant()));
        sender.send(contact.get().phone(), template.render(contact.get().name(), args));

        // Neither phone nor message body in the log: both are personal data.
        log.info("Aviso {} entregue ao aluno {}", template, studentId);
    }

    private void markDiscarded(Long studentId, MessageTemplate template, String referenceKey) {
        logs.saveAndFlush(new NotificationLog(studentId, template, referenceKey, clock.instant(),
                DeliveryStatus.DISCARDED));
    }
}
