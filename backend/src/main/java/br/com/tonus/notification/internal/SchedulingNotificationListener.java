package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.MessageTemplate;
import br.com.tonus.registration.RegistrationFacade;
import br.com.tonus.registration.StudentSummary;
import br.com.tonus.scheduling.BroadcastRequested;
import br.com.tonus.scheduling.ClassCanceled;
import br.com.tonus.scheduling.ClassRescheduled;
import br.com.tonus.scheduling.AnnouncementCreated;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Whatever changes in the student's schedule, they get to know about it.
 *
 * Delivery is at-least-once, so each method here runs more than once for
 * the same fact; idempotency lives in notification_log's unique constraint,
 * checked in NotificationService.
 */
@Component
class SchedulingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SchedulingNotificationListener.class);

    private final NotificationService notifications;
    private final RegistrationFacade registration;
    private final NotificationProperties properties;

    SchedulingNotificationListener(NotificationService notifications, RegistrationFacade registration,
                                   NotificationProperties properties) {
        this.notifications = notifications;
        this.registration = registration;
        this.properties = properties;
    }

    @ApplicationModuleListener
    public void on(ClassCanceled event) {
        notifications.notifyStudent(event.studentId(), MessageTemplate.CLASS_CANCELED,
                event.date().toString(), MessageTemplate.date(event.date()));
    }

    /**
     * The key carries both origin AND destination. The same class can be
     * rescheduled twice from the same day — deleting the exception and
     * recreating it republishes the event with an equal originalDate and a
     * different destination —, and keying only on the origin would make the
     * second notice be confused with a redelivery of the first.
     */
    @ApplicationModuleListener
    public void on(ClassRescheduled event) {
        String reference = "%s|%s|%s".formatted(event.originalDate(), event.newDate(), event.newTime());
        notifications.notifyStudent(event.studentId(), MessageTemplate.CLASS_RESCHEDULED, reference,
                MessageTemplate.date(event.originalDate()),
                MessageTemplate.date(event.newDate()),
                MessageTemplate.time(event.newTime()));
    }

    /**
     * Announcement is the only event that turns into N messages.
     *
     * Who gets notified comes from registration, not from scheduling: the
     * natural consumer would be whoever knows who has class that day, but
     * notification doesn't depend on scheduling for queries, and opening
     * that edge for one event doesn't pay off. The price is also notifying
     * an active student with no default schedule.
     *
     * One student's failure doesn't interrupt the others — each commits on
     * their own —, but at the end the exception is mandatory: without it
     * Modulith would mark the event as delivered and whoever was left out
     * would never be notified.
     *
     * Consecutive failures, however, do interrupt. It's not bad luck with N
     * students, it's the gateway being down, and retrying costs one read
     * timeout per student: with 500 students and a hung Evolution, the loop
     * holds an executor thread for over an hour and the next holiday waits
     * behind it. Aborting early reaches the same place — the publication
     * stays incomplete and the republication job tries again later — without
     * paying the toll.
     *
     * The idempotency key is the announcement's id, not the date: a delete +
     * recreate on the same day is a *new* fact, and keying on the date would
     * make its notice be read as a redelivery of the old announcement and
     * swallowed by the UNIQUE in notification_log.
     */
    @ApplicationModuleListener
    public void on(AnnouncementCreated event) {
        /*
         * The template is chosen by the effect, and it's not just the text
         * that changes: it enters the idempotency key together with the
         * reference (the announcement's id), so "we won't have class" and
         * "classes happen normally" are opposite notices, and sending the
         * former for an announcement that doesn't suspend classes would
         * empty the room on a class day.
         */
        MessageTemplate template = event.suspendsClasses()
                ? MessageTemplate.ANNOUNCEMENT_SUSPENSION
                : MessageTemplate.ANNOUNCEMENT_NOTICE;
        fanOutToActiveStudents("comunicado", event.announcementId().toString(), template,
                MessageTemplate.date(event.date()), event.description());
    }

    /**
     * Manual trigger (change 9): same fan-out as the announcement, free-form
     * message instead of date+description. The idempotency key is the
     * broadcastId, not the date — the ADMIN can legitimately trigger it
     * twice on the same day, and those are two independent notices, not a
     * redelivery of the first.
     */
    @ApplicationModuleListener
    public void on(BroadcastRequested event) {
        fanOutToActiveStudents("disparo", event.broadcastId(), MessageTemplate.BROADCAST, event.message());
    }

    /**
     * One event turns into N messages, one per active student — who they
     * are comes from registration, not from scheduling: notification
     * doesn't depend on scheduling for queries, and opening that edge
     * because of this doesn't pay off. The price is also notifying an
     * active student with no default schedule.
     *
     * One student's failure doesn't interrupt the others — each commits on
     * their own in {@link NotificationService#notifyStudent} —, but at the
     * end the exception is mandatory: without it Modulith would mark the
     * event as delivered and whoever was left out would never be notified.
     *
     * Consecutive failures, however, do interrupt. It's not bad luck with N
     * students, it's the gateway being down, and retrying costs one read
     * timeout per student: with 500 students and a hung Evolution, the loop
     * holds an executor thread for over an hour. Aborting early reaches the
     * same place — the publication stays incomplete and the republication
     * job tries again later — without paying the toll.
     */
    private void fanOutToActiveStudents(String kind, String reference, MessageTemplate template, Object... args) {
        int limit = properties.consecutiveFailureLimit();
        List<Long> failed = new ArrayList<>();
        int consecutiveFailures = 0;

        for (StudentSummary student : registration.findActiveStudents()) {
            try {
                notifications.notifyStudent(student.studentId(), template, reference, args);
                consecutiveFailures = 0;
            } catch (WhatsAppDeliveryException e) {
                // Only a delivery failure is "the gateway being down" — the
                // one condition that justifies both counting toward the abort
                // and treating the fan-out as pending. A programming error
                // (NPE, template mismatch) must propagate as itself: swallowed
                // here, it would look like five consecutive gateway failures,
                // the whole publication would be retried for a day, and the
                // developer reading the WARN would hunt the gateway instead
                // of the code.
                log.warn("Falha ao avisar o aluno {} do {} {}", student.studentId(), kind, reference, e);
                failed.add(student.studentId());
                if (++consecutiveFailures >= limit) {
                    throw new WhatsAppDeliveryException(
                            "%s %s abortado após %d falhas seguidas; pendentes a partir de %s"
                                    .formatted(kind, reference, consecutiveFailures, failed));
                }
            }
        }

        if (!failed.isEmpty()) {
            throw new WhatsAppDeliveryException(
                    "%s %s não avisado aos alunos %s".formatted(kind, reference, failed));
        }
    }
}
