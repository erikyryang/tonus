package br.com.tonus.billing;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The invoice becomes one per student, with the amount summed across plans
 * (SPEC-012).
 *
 * The suite clock is fixed at 2026-07-27, and a student created via the API is
 * born with `created_at` on that day — so the due date falls today (rule from
 * change 3) and the daily job bills it. That is what makes these tests possible
 * without seeding the registration date by hand.
 */
class InvoiceFromPlansIT extends AbstractBillingIntegrationTest {

    @Autowired
    private br.com.tonus.billing.internal.BillingService billingService;

    @Test
    void singlePlanBillsItsPrice() throws Exception {
        long studentId = newStudent("Aluno De Um Plano");
        enroll(studentId, createPlan(unique("Plano Solo"), "350.00"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("350.00");
    }

    /** Acceptance criterion: 350 + 270 with 10% discount = 558.00. */
    @Test
    void twoPlansApplyTheConfiguredDiscount() throws Exception {
        setDiscount("10.00");
        long studentId = newStudent("Aluno De Dois Planos Com Desconto");
        enroll(studentId, createPlan(unique("Musculação Desconto"), "350.00"));
        enroll(studentId, createPlan(unique("Pilates Desconto"), "270.00"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("558.00");
    }

    /** The discount is configuration, not a hardcoded rule: with zero, it's a pure sum. */
    @Test
    void twoPlansWithoutDiscountSumUp() throws Exception {
        setDiscount("0.00");
        long studentId = newStudent("Aluno De Dois Planos Sem Desconto");
        enroll(studentId, createPlan(unique("Musculação Cheia"), "350.00"));
        enroll(studentId, createPlan(unique("Pilates Cheio"), "270.00"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("620.00");
    }

    /** A deactivated plan drops out of the bill, even with an active enrollment. */
    @Test
    void inactivePlanIsNotBilled() throws Exception {
        setDiscount("0.00");
        long studentId = newStudent("Aluno Do Plano Desativado");
        long kept = createPlan(unique("Plano Que Fica"), "350.00");
        long dropped = createPlan(unique("Plano Que Sai"), "270.00");
        enroll(studentId, kept);
        enroll(studentId, dropped);
        mockMvc.perform(patch("/plans/{id}/deactivate", dropped).cookie(admin)).andExpect(status().isOk());

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("350.00");
    }

    /** A deactivated enrollment drops out of the bill, even with an active plan. */
    @Test
    void inactiveEnrollmentIsNotBilled() throws Exception {
        setDiscount("0.00");
        long studentId = newStudent("Aluno Da Matricula Pausada");
        long kept = createPlan(unique("Plano Mantido"), "350.00");
        long paused = createPlan(unique("Plano Pausado"), "270.00");
        enroll(studentId, kept);
        long pausedEnrollment = enroll(studentId, paused);
        mockMvc.perform(patch("/plan-enrollments/{id}/deactivate", pausedEnrollment).cookie(admin))
                .andExpect(status().isOk());

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("350.00");
    }

    @Test
    void studentWithoutActivePlanIsNotBilled() throws Exception {
        long studentId = newStudent("Aluno Sem Plano Nenhum");

        billingService.generateDueInvoices();

        assertThat(invoiceCountOf(studentId)).isZero();
    }

    /**
     * One invoice per student per period, even with two plans and even
     * when the job runs twice: idempotence is the
     * uk_invoice_student_period constraint, not the goodwill of the loop.
     */
    @Test
    void oneInvoicePerStudentPerPeriodEvenWithSeveralPlansAndRuns() throws Exception {
        long studentId = newStudent("Aluno De Fatura Unica");
        enroll(studentId, createPlan(unique("Plano Um De Dois"), "100.00"));
        enroll(studentId, createPlan(unique("Plano Dois De Dois"), "200.00"));

        billingService.generateDueInvoices();
        billingService.generateDueInvoices();

        assertThat(invoiceCountOf(studentId)).isEqualTo(1);
    }

    /**
     * Rounding only once, on the total — and the case distinguishes the two
     * calculations: (100.01 + 0.01) × 0.6667 = 66.6833… → <strong>66.68</strong>,
     * while rounding plan by plan would give 66.68 + 0.01 = 66.69. A cent per
     * student per month is the kind of difference nobody finds later.
     */
    @Test
    void roundsOnceOnTheTotal() throws Exception {
        setDiscount("33.33");
        long studentId = newStudent("Aluno Do Arredondamento");
        enroll(studentId, createPlan(unique("Plano Quebrado Um"), "100.01"));
        enroll(studentId, createPlan(unique("Plano Quebrado Dois"), "0.01"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("66.68");
    }

    private long newStudent(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor De " + name)));
    }

    private void setDiscount(String percent) throws Exception {
        mockMvc.perform(put("/billing-settings")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"multiPlanDiscountPercent":"%s"}
                                """.formatted(percent)))
                .andExpect(status().isOk());
    }

    private BigDecimal amountOf(long studentId) {
        return jdbcTemplate.queryForObject(
                "SELECT amount FROM invoice WHERE student_id = ?", BigDecimal.class, studentId);
    }

    private int invoiceCountOf(long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice WHERE student_id = ?", Integer.class, studentId);
        return count == null ? 0 : count;
    }
}
