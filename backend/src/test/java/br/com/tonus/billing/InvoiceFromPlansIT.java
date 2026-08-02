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
 * A fatura passa a ser uma por aluno, com valor somado dos planos (SPEC-012).
 *
 * O relógio da suíte é fixo em 2026-07-27, e o aluno criado pela API nasce com
 * `created_at` desse dia — então o vencimento dele cai hoje (regra da mudança
 * 3) e o job do dia o fatura. É o que torna estes testes possíveis sem semear
 * data de cadastro à mão.
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

    /** Critério de aceite: 350 + 270 com 10% = 558,00. */
    @Test
    void twoPlansApplyTheConfiguredDiscount() throws Exception {
        setDiscount("10.00");
        long studentId = newStudent("Aluno De Dois Planos Com Desconto");
        enroll(studentId, createPlan(unique("Musculação Desconto"), "350.00"));
        enroll(studentId, createPlan(unique("Pilates Desconto"), "270.00"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("558.00");
    }

    /** O desconto é configuração, não regra embutida: com zero, soma pura. */
    @Test
    void twoPlansWithoutDiscountSumUp() throws Exception {
        setDiscount("0.00");
        long studentId = newStudent("Aluno De Dois Planos Sem Desconto");
        enroll(studentId, createPlan(unique("Musculação Cheia"), "350.00"));
        enroll(studentId, createPlan(unique("Pilates Cheio"), "270.00"));

        billingService.generateDueInvoices();

        assertThat(amountOf(studentId)).isEqualByComparingTo("620.00");
    }

    /** Plano desativado sai da conta, mesmo com a matrícula ativa. */
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

    /** Matrícula desativada sai da conta, mesmo com o plano ativo. */
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
     * Uma fatura por aluno por competência, mesmo com dois planos e mesmo
     * rodando o job duas vezes: a idempotência é a constraint
     * uk_invoice_student_period, não a boa vontade do laço.
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
     * Arredondamento uma vez só, no total — e o caso distingue as duas contas:
     * (100,01 + 0,01) × 0,6667 = 66,6833… → <strong>66,68</strong>, enquanto
     * arredondar plano a plano daria 66,68 + 0,01 = 66,69. Um centavo por aluno
     * por mês é o tipo de diferença que ninguém encontra depois.
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
