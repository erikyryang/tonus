package br.com.tonus.billing;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baixa de fatura: POST /invoices/{id}/payment. Tudo acontece na mesma
 * transação (spec): fatura vira PAGA, pagamento é criado e o evento
 * PaymentRegistered é publicado.
 *
 * A baixa <strong>não</strong> mexe no vencimento da assinatura. Mexia — o dia
 * do pagamento virava o novo dia de vencimento —, e a regra foi substituída
 * pelo dia do cadastro do aluno (SPEC-004). Os testes daqui seguram a
 * substituição: pagar em qualquer data deixa o {@code due_day} onde estava.
 *
 * {@code ApplicationEvents} (spring-test), e não
 * {@code AssertablePublishedEvents} do Modulith: o resolver deste último é
 * package-private, só disponível dentro de {@code @ApplicationModuleTest}
 * (ver {@code BillingJobIT}). Aqui o teste é MockMvc de ponta a ponta, então a
 * captura genérica do Spring é o que está ao alcance.
 */
@RecordApplicationEvents
class PaymentIT extends AbstractBillingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registeringPaymentClosesInvoiceAndLeavesDueDayAlone(ApplicationEvents events) throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa"), createTeacher(unique("Professor Da Baixa")));
        long subscriptionId = createSubscription(studentId, "200.00");
        int dueDayBefore = dueDayOf(subscriptionId);
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        pay(invoiceId, TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(paymentCountFor(invoiceId)).isEqualTo(1);
        assertThat(dueDayOf(subscriptionId)).isEqualTo(dueDayBefore);
        assertThat(auditActionsFor(invoiceId)).contains("UPDATE");
        assertThat(events.stream(PaymentRegistered.class)).hasSize(1);
    }

    /**
     * SPEC-008: a baixa é lançada de um painel, e o dinheiro raramente entra no
     * mesmo dia em que alguém digita. Quando o ADMIN informa a data, é ela — e
     * não "hoje" — que vira o pagamento.
     *
     * E é só isso que ela vira: uma data de pagamento distante do vencimento não
     * move mais o vencimento. Pagar em atraso empurrava a mensalidade para o dia
     * do atraso, mês após mês.
     */
    @Test
    void chosenPaymentDateDrivesThePaymentAndNothingElse() throws Exception {
        long studentId = createStudent(unique("Aluno Da Data"), createTeacher(unique("Professor Da Data")));
        long subscriptionId = createSubscription(studentId, "200.00");
        int dueDayBefore = dueDayOf(subscriptionId);
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        pay(invoiceId, "2026-07-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentDate").value("2026-07-20"));

        assertThat(paymentDateOf(invoiceId)).isEqualTo(LocalDate.parse("2026-07-20"));
        assertThat(dueDayOf(subscriptionId)).isEqualTo(dueDayBefore);
    }

    /**
     * Data futura é barrada no back, não só no formulário: aceitá-la mandaria ao
     * aluno o recibo de um pagamento que ainda não aconteceu.
     */
    @Test
    void futurePaymentDateIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Do Futuro"), createTeacher(unique("Professor Do Futuro")));
        long subscriptionId = createSubscription(studentId, "200.00");
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        String tomorrow = LocalDate.parse(TODAY).plusDays(1).toString();

        pay(invoiceId, tomorrow).andExpect(status().isBadRequest());

        assertThat(paymentCountFor(invoiceId)).isZero();
    }

    /**
     * A data é obrigatória: não existe "omitiu, então é hoje". Este endpoint
     * dispara recibo por WhatsApp, e um default silencioso o dispararia a partir
     * de um corpo esquecido.
     */
    @Test
    void paymentDateIsRequired() throws Exception {
        long studentId = createStudent(unique("Aluno Sem Data"), createTeacher(unique("Professor Sem Data")));
        long subscriptionId = createSubscription(studentId, "200.00");
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId).cookie(admin))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(paymentCountFor(invoiceId)).isZero();
    }

    @Test
    void payingAlreadyPaidInvoiceIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa Dupla"), createTeacher(unique("Professor Da Baixa Dupla")));
        long subscriptionId = createSubscription(studentId, "200.00");
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "PAID");

        pay(invoiceId, TODAY).andExpect(status().isConflict());
    }

    @Test
    void payingUnknownInvoiceReturnsNotFound() throws Exception {
        pay(999_999L, TODAY).andExpect(status().isNotFound());
    }

    private ResultActions pay(long invoiceId, String paymentDate) throws Exception {
        return mockMvc.perform(post("/invoices/{id}/payment", invoiceId)
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentDate":"%s"}
                        """.formatted(paymentDate)));
    }

    private LocalDate paymentDateOf(long invoiceId) {
        return jdbcTemplate.queryForObject(
                "SELECT payment_date FROM payment WHERE invoice_id = ?", LocalDate.class, invoiceId);
    }

    private int paymentCountFor(long invoiceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE invoice_id = ?", Integer.class, invoiceId);
        return count == null ? 0 : count;
    }

    private int dueDayOf(long subscriptionId) {
        Integer dueDay = jdbcTemplate.queryForObject(
                "SELECT due_day FROM subscription WHERE id = ?", Integer.class, subscriptionId);
        return dueDay == null ? -1 : dueDay;
    }

    private List<String> auditActionsFor(long invoiceId) {
        return jdbcTemplate.queryForList("""
                SELECT action FROM audit_log
                WHERE entity_name = 'Invoice' AND entity_id = ?
                ORDER BY id
                """, String.class, String.valueOf(invoiceId));
    }
}
