package br.com.tonus.notification;

import br.com.tonus.scheduling.ClassCanceled;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The student ends up being notified, even when the first delivery fails.
 *
 * It exists because the earlier test stopped too early: it asserted that the
 * publication stayed incomplete and called it done. Staying incomplete is the
 * mechanism, not the result — and the mechanism was right while nobody
 * reprocessed, so the suite stayed green with the notice never going out. A
 * test that describes the means instead of the end does not protect what
 * matters.
 *
 * It reprocesses through the Modulith API, not the EventRepublicationJob: the
 * job lives in shared and what triggers it in production is the cron. What has
 * to hold here is that the redelivery reaches the student; that the job passes
 * the configured age is the concern of {@code EventRepublicationJobTest}.
 */
class NotificationRetryIT extends AbstractNotificationIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");

    @Autowired
    private IncompleteEventPublications incompletePublications;

    @Test
    void studentIsNotifiedAfterTheFailedDeliveryIsReprocessed() {
        Student student = seedStudent("Aluno Da Segunda Chance");
        sender.failFor(student.phone());

        publish(new ClassCanceled(student.id(), MONDAY));
        eventually("primeira tentativa falhou",
                () -> sender.countAttemptsTo(student.phone()) >= 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(countIncompletePublications("ClassCanceled", student.id())).isEqualTo(1);

        // Evolution comes back, and the job sweeps the registry.
        sender.reset();
        incompletePublications.resubmitIncompletePublications(publication -> true);

        eventually("aviso entregue no reprocessamento",
                () -> sender.countSentTo(student.phone()) == 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
        eventually("publicação finalmente concluída",
                () -> countIncompletePublications("ClassCanceled", student.id()) == 0);
    }

    /**
     * Reprocessing does not resend what already went out. Same constraint as
     * always, now exercised through the path the job uses — which is where a
     * mass resend would happen if idempotency failed.
     */
    @Test
    void reprocessingDoesNotResendWhatAlreadyWentOut() {
        Student student = seedStudent("Aluno Ja Avisado No Retry");

        publish(new ClassCanceled(student.id(), MONDAY));
        eventually("aviso entregue", () -> sender.countSentTo(student.phone()) == 1);
        eventually("entrega concluída",
                () -> countIncompletePublications("ClassCanceled", student.id()) == 0);

        incompletePublications.resubmitIncompletePublications(publication -> true);

        assertThat(sender.countSentTo(student.phone())).isEqualTo(1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
    }
}
