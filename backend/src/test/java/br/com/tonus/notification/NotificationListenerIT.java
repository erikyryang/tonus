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
 * Os critérios de aceite da SPEC-005 que valem para um aluno só. O fan-out de
 * comunicado tem classe própria (AnnouncementFanOutIT).
 *
 * Os eventos entram por publicação direta, e não chamando SchedulingService ou
 * BillingService: o que está sob teste é o consumo, e amarrar cada caso à
 * operação que publica traria junto as regras de agenda e de cobrança — teste
 * que falha por motivo alheio ao que ele afirma.
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
     * Critério de aceite: evento reprocessado com envio já gravado não reenvia.
     *
     * A espera é pelas duas publicações concluídas, não por um tempo fixo: a
     * segunda entrega tem de ter terminado para a asserção significar
     * "não reenviou" em vez de "ainda não rodou".
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
     * A chave de idempotência de remarcação leva origem e destino. Remarcar de
     * novo, a partir do mesmo dia, é fato novo e precisa de aviso novo — com a
     * data original sozinha na chave, o aluno ficaria sem saber.
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
     * Critério de aceite: Evolution fora do ar não grava nada e deixa a
     * publicação incompleta, para o registry reprocessar depois.
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
     * Critério de aceite: aluno que a fachada não devolve mais não recebe
     * mensagem e não deixa evento preso. Reprocessar não traria o telefone de
     * volta — insistir só entupiria o registry.
     */
    /**
     * Quem saiu do estúdio não recebe mais nada.
     *
     * Caminho distinto do aluno inexistente, e foi por confundir os dois que o
     * bug passou: {@code findStudentContact} usa {@code findById} e devolve
     * inativo normalmente, então o aviso saía. O caso realista é cobrança —
     * aluno sai devendo, a assinatura segue ativa, e ele passa a receber
     * lembrete de fatura atrasada indefinidamente.
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
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(countLogs(student.id(), "INVOICE_OVERDUE")).isZero();
    }

    @Test
    void unknownStudentIsSkippedAndPublicationCompletes() {
        long ghostId = 999_999_999L;
        int sentBefore = sender.sent().size();

        publish(new ClassCanceled(ghostId, MONDAY));

        eventually("publicação concluída mesmo sem aluno",
                () -> countCompletedPublications("ClassCanceled", ghostId) == 1);
        assertThat(countLogs(ghostId, "CLASS_CANCELED")).isZero();
        // Diferencial, e não "está vazio": o dublê é compartilhado pela classe
        // e um listener de teste anterior pode gravar depois do reset.
        assertThat(sender.sent()).hasSize(sentBefore);
    }
}
