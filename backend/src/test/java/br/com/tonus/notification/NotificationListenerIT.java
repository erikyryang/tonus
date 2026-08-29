package br.com.tonus.notification;

import br.com.tonus.billing.InvoiceDueSoon;
import br.com.tonus.billing.InvoiceOverdue;
import br.com.tonus.billing.PaymentRegistered;
import br.com.tonus.scheduling.ClassCanceled;
import br.com.tonus.scheduling.ClassRescheduled;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SPEC-005 acceptance criteria that hold for a single student. The
 * announcement fan-out has its own class (AnnouncementFanOutIT).
 *
 * The events enter through direct publication, not by calling SchedulingService
 * or BillingService: what is under test is consumption, and tying each case to
 * the operation that publishes it would drag in the scheduling and billing
 * rules — a test that fails for a reason unrelated to what it asserts.
 */
class NotificationListenerIT extends AbstractNotificationIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");
    private static final LocalDate WEDNESDAY = LocalDate.parse("2026-07-29");

    @Test
    void classCanceledNotifiesStudent() {
        Student student = seedStudent("Aluno Cancelamento");

        publish(new ClassCanceled(student.id(), MONDAY));

        eventually("aviso de cancelamento enviado",
                () -> sender.countSentTo(student.phone()) == 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
        assertThat(sender.sent().getLast().message()).contains(student.name(), "27/07/2026");
    }

    @Test
    void classRescheduledNotifiesOriginAndDestination() {
        Student student = seedStudent("Aluno Remarcacao");

        publish(new ClassRescheduled(student.id(), MONDAY, WEDNESDAY, LocalTime.of(8, 0)));

        eventually("aviso de remarcação enviado",
                () -> sender.countSentTo(student.phone()) == 1);
        assertThat(sender.sent().getLast().message())
                .contains("27/07/2026", "29/07/2026", "08:00");
    }

    @Test
    void billingEventsNotifyStudent() {
        Student student = seedStudent("Aluno Cobranca");
        LocalDate dueDate = LocalDate.parse("2026-08-10");

        publish(new InvoiceDueSoon(1_001L, student.id(), dueDate, new BigDecimal("250.00")));
        publish(new InvoiceOverdue(1_002L, student.id(), dueDate));
        publish(new PaymentRegistered(1_003L, student.id(), dueDate, new BigDecimal("250.00")));

        eventually("os três avisos de cobrança enviados",
                () -> sender.countSentTo(student.phone()) == 3);
        assertThat(countLogs(student.id(), "INVOICE_DUE_SOON")).isEqualTo(1);
        assertThat(countLogs(student.id(), "INVOICE_OVERDUE")).isEqualTo(1);
        assertThat(countLogs(student.id(), "PAYMENT_REGISTERED")).isEqualTo(1);
    }

    /**
     * Acceptance criterion: a reprocessed event with a delivery already
     * recorded does not resend.
     *
     * The wait is for the two completed publications, not a fixed time: the
     * second delivery has to have finished for the assertion to mean "did not
     * resend" instead of "has not run yet".
     */
    @Test
    void reprocessedEventDoesNotResend() {
        Student student = seedStudent("Aluno Reprocessado");
        ClassCanceled event = new ClassCanceled(student.id(), MONDAY);

        publish(event);
        eventually("primeira entrega concluída",
                () -> countCompletedPublications("ClassCanceled", student.id()) == 1);

        publish(event);
        eventually("segunda entrega concluída",
                () -> countCompletedPublications("ClassCanceled", student.id()) == 2);

        assertThat(sender.countSentTo(student.phone())).isEqualTo(1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
    }

    /**
     * The rescheduling idempotency key carries origin and destination.
     * Rescheduling again, from the same day, is a new fact and needs a new
     * notice — with only the original date in the key, the student would never
     * find out.
     */
    @Test
    void reschedulingAgainFromSameOriginNotifiesAgain() {
        Student student = seedStudent("Aluno Remarcado Duas Vezes");

        publish(new ClassRescheduled(student.id(), MONDAY, WEDNESDAY, LocalTime.of(8, 0)));
        eventually("primeiro aviso enviado", () -> sender.countSentTo(student.phone()) == 1);

        publish(new ClassRescheduled(student.id(), MONDAY, WEDNESDAY, LocalTime.of(10, 0)));

        eventually("segundo aviso enviado", () -> sender.countSentTo(student.phone()) == 2);
        assertThat(countLogs(student.id(), "CLASS_RESCHEDULED")).isEqualTo(2);
    }

    /**
     * Acceptance criterion: Evolution down records nothing and leaves the
     * publication incomplete, so the registry can reprocess it later.
     */
    @Test
    void deliveryFailureLogsNothingAndLeavesPublicationIncomplete() {
        Student student = seedStudent("Aluno Sem Evolution");
        sender.failFor(student.phone());

        publish(new ClassCanceled(student.id(), MONDAY));

        eventually("tentativa de envio ocorreu",
                () -> sender.countAttemptsTo(student.phone()) >= 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(sender.countSentTo(student.phone())).isZero();
        assertThat(countIncompletePublications("ClassCanceled", student.id())).isEqualTo(1);
    }

    /**
     * Acceptance criterion: a student the facade no longer returns receives no
     * message and does not leave the event stuck. Reprocessing would not bring
     * the phone number back — insisting would only clog the registry.
     */
    /**
     * Whoever left the studio receives nothing more.
     *
     * A distinct path from the nonexistent student, and it was by confusing
     * the two that the bug slipped through: {@code findStudentContact} uses
     * {@code findById} and returns inactive students normally, so the notice
     * went out. The realistic case is billing — the student leaves owing, the
     * subscription stays active, and they start receiving overdue-invoice
     * reminders indefinitely.
     *
     * The log counts one DISCARDED row per skipped event: the row is the
     * idempotency marker that stops a later redelivery of the same event from
     * sending a stale notice if the student ever comes back.
     */
    @Test
    void departedStudentReceivesNothingAndDoesNotBlockTheEvent() {
        Student student = seedStudent("Aluno Que Saiu Do Estudio");
        jdbcTemplate.update("UPDATE student SET active = FALSE WHERE id = ?", student.id());

        publish(new ClassCanceled(student.id(), MONDAY));
        publish(new InvoiceOverdue(2_001L, student.id(), MONDAY));

        eventually("os dois eventos concluídos sem envio",
                () -> countCompletedPublications("ClassCanceled", student.id()) == 1
                        && countCompletedPublications("InvoiceOverdue", student.id()) == 1);
        assertThat(sender.countSentTo(student.phone())).isZero();
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
        assertThat(countLogs(student.id(), "INVOICE_OVERDUE")).isEqualTo(1);
    }

    @Test
    void unknownStudentIsSkippedAndPublicationCompletes() {
        long ghostId = 999_999_999L;
        int sentBefore = sender.sent().size();

        publish(new ClassCanceled(ghostId, MONDAY));

        eventually("publicação concluída mesmo sem aluno",
                () -> countCompletedPublications("ClassCanceled", ghostId) == 1);
        // One DISCARDED row: the ghost is "not active" by definition, and the
        // marker is recorded for the same reason as for a departed student.
        assertThat(countLogs(ghostId, "CLASS_CANCELED")).isEqualTo(1);
        // The differential, not "is empty": the fake is shared by the class
        // and an earlier test listener may record after the reset.
        assertThat(sender.sent()).hasSize(sentBefore);
    }

    /**
     * The reason the DISCARDED row exists: a redelivery of the same event
     * (the publication stayed incomplete because the fan-out failed on
     * another student) must not send a stale notice to a student who was
     * reactivated in the meantime.
     */
    @Test
    void reactivatedStudentDoesNotGetTheDiscardedNoticeOnRedelivery() {
        Student student = seedStudent("Aluno Reativado Depois Do Descarte");
        jdbcTemplate.update("UPDATE student SET active = FALSE WHERE id = ?", student.id());
        publish(new ClassCanceled(student.id(), MONDAY));
        eventually("descarte registrado", () -> countLogs(student.id(), "CLASS_CANCELED") == 1);

        jdbcTemplate.update("UPDATE student SET active = TRUE WHERE id = ?", student.id());
        publish(new ClassCanceled(student.id(), MONDAY));

        eventually("reentrega processada sem novo envio",
                () -> countCompletedPublications("ClassCanceled", student.id()) == 2);
        assertThat(sender.countSentTo(student.phone())).isZero();
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
    }
}
