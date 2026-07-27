package br.com.tonus.billing;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baixa de fatura: POST /invoices/{id}/payment. Tudo acontece na mesma
 * transação (spec): fatura vira PAGA, pagamento é criado, assinatura recalcula
 * o dia de vencimento, e o evento PaymentRegistered é publicado.
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
    void registeringPaymentClosesInvoiceAndRecalculatesDueDay(ApplicationEvents events) throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa"), createTeacher(unique("Professor Da Baixa")));
        long subscriptionId = createSubscription(studentId, "200.00", 5);
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // O vencimento vira o aniversário do pagamento: dia de hoje (BillingClockConfig).
        assertThat(paymentCountFor(invoiceId)).isEqualTo(1);
        assertThat(dueDayOf(subscriptionId)).isEqualTo(LocalDate.parse(TODAY).getDayOfMonth());
        assertThat(auditActionsFor(invoiceId)).contains("UPDATE");
        assertThat(events.stream(PaymentRegistered.class)).hasSize(1);
    }

    @Test
    void payingAlreadyPaidInvoiceIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa Dupla"), createTeacher(unique("Professor Da Baixa Dupla")));
        long subscriptionId = createSubscription(studentId, "200.00", 5);
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-05", "PAID");

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId).cookie(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void payingUnknownInvoiceReturnsNotFound() throws Exception {
        mockMvc.perform(post("/invoices/{id}/payment", 999_999L).cookie(admin))
                .andExpect(status().isNotFound());
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
