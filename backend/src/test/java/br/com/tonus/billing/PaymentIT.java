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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invoice write-off: POST /invoices/{id}/payment. Everything happens in the
 * same transaction (spec): the invoice becomes PAID, the payment is created
 * and the PaymentRegistered event is published.
 *
 * The write-off does <strong>not</strong> touch the subscription due date. It
 * used to — the payment day became the new due day —, and the rule was
 * replaced by the student's registration day (SPEC-004). The tests here hold
 * the replacement: paying on any date leaves the {@code due_day} where it was.
 *
 * {@code ApplicationEvents} (spring-test), not the Modulith's
 * {@code AssertablePublishedEvents}: the latter's resolver is
 * package-private, only available inside {@code @ApplicationModuleTest}
 * (see {@code BillingJobIT}). Here the test is end-to-end MockMvc, so the
 * Spring generic capture is what is within reach.
 */
@RecordApplicationEvents
class PaymentIT extends AbstractBillingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registeringPaymentClosesInvoiceAndLeavesDueDayAlone(ApplicationEvents events) throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa"), createTeacher(unique("Professor Da Baixa")));
        long invoiceId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        pay(invoiceId, TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(paymentCountFor(invoiceId)).isEqualTo(1);
        assertThat(auditActionsFor(invoiceId)).contains("UPDATE");
        assertThat(events.stream(PaymentRegistered.class)).hasSize(1);
    }

    /**
     * SPEC-008: the write-off is posted from a dashboard, and money rarely
     * comes in on the same day someone types it. When the ADMIN provides a
     * date, it is that date — not "today" — that becomes the payment.
     *
     * And that is all it becomes: a payment date far from the due date no
     * longer moves the due date. Paying late used to push the monthly payment
     * to the day of the delay, month after month.
     */
    @Test
    void chosenPaymentDateDrivesThePaymentAndNothingElse() throws Exception {
        long studentId = createStudent(unique("Aluno Da Data"), createTeacher(unique("Professor Da Data")));
        long invoiceId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        pay(invoiceId, "2026-07-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentDate").value("2026-07-20"));

        assertThat(paymentDateOf(invoiceId)).isEqualTo(LocalDate.parse("2026-07-20"));
    }

    /**
     * A future date is blocked in the backend, not just in the form: accepting
     * it would send the student the receipt of a payment that has not happened
     * yet.
     */
    @Test
    void futurePaymentDateIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Do Futuro"), createTeacher(unique("Professor Do Futuro")));
        long invoiceId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        String tomorrow = LocalDate.parse(TODAY).plusDays(1).toString();

        pay(invoiceId, tomorrow).andExpect(status().isBadRequest());

        assertThat(paymentCountFor(invoiceId)).isZero();
    }

    /**
     * The date is mandatory: there is no "omitted, so it is today". This
     * endpoint fires a WhatsApp receipt, and a silent default would fire it
     * from a forgotten body.
     */
    @Test
    void paymentDateIsRequired() throws Exception {
        long studentId = createStudent(unique("Aluno Sem Data"), createTeacher(unique("Professor Sem Data")));
        long invoiceId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-05", "OPEN");

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId).cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/invoices/{id}/payment", invoiceId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(paymentCountFor(invoiceId)).isZero();
    }

    @Test
    void payingAlreadyPaidInvoiceIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Da Baixa Dupla"), createTeacher(unique("Professor Da Baixa Dupla")));
        long invoiceId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-05", "PAID");

        pay(invoiceId, TODAY).andExpect(status().isConflict());
    }

    @Test
    void payingUnknownInvoiceReturnsNotFound() throws Exception {
        pay(999_999L, TODAY).andExpect(status().isNotFound());
    }

    private ResultActions pay(long invoiceId, String paymentDate) throws Exception {
        return mockMvc.perform(post("/invoices/{id}/payment", invoiceId)
                .cookie(admin)
                .with(csrf())
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

    private List<String> auditActionsFor(long invoiceId) {
        return jdbcTemplate.queryForList("""
                SELECT action FROM audit_log
                WHERE entity_name = 'Invoice' AND entity_id = ?
                ORDER BY id
                """, String.class, String.valueOf(invoiceId));
    }
}
