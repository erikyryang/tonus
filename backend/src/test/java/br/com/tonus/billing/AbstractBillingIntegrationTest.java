package br.com.tonus.billing;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes do módulo billing.
 *
 * Plano e matrícula são criados pela própria API, mas fatura não tem endpoint
 * de criação — ela só nasce do job diário. Testes que precisam de uma fatura
 * existente semeiam a linha direto por SQL, no mesmo espírito de
 * {@code SchedulingEventsIT.seedStudent}: contornar a ausência de um caminho
 * HTTP que não existe por decisão de arquitetura, não por lacuna a preencher.
 */
@Import(BillingClockConfig.class)
abstract class AbstractBillingIntegrationTest extends AbstractRegistrationIntegrationTest {

    /** "Hoje" para {@link BillingClockConfig}. */
    protected static final String TODAY = "2026-07-27";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected long createPlan(String name, String monthlyPrice) throws Exception {
        return idOf(mockMvc.perform(post("/plans")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","monthlyPrice":"%s"}
                                """.formatted(name, monthlyPrice)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected long enroll(long studentId, long planId) throws Exception {
        return idOf(postEnrollment(studentId, planId)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postEnrollment(long studentId, long planId) throws Exception {
        return mockMvc.perform(post("/plan-enrollments")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":%d,"planId":%d}
                        """.formatted(studentId, planId)));
    }

    /** A fatura é do aluno desde a SPEC-012; não existe mais assinatura para semear. */
    protected long seedInvoice(long studentId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, studentId, referenceMonth, amount, dueDate, status);
    }

    /**
     * Baixa já registrada. Serve à listagem, que precisa exibir a data do
     * pagamento de uma fatura PAGA sem ter passado pelo endpoint de baixa.
     */
    protected void seedPayment(long invoiceId, String paymentDate, long registeredBy) {
        jdbcTemplate.update("""
                INSERT INTO payment (invoice_id, payment_date, registered_by, created_at, updated_at)
                VALUES (?, ?::date, ?, now(), now())
                """, invoiceId, paymentDate, registeredBy);
    }

    /**
     * Cookie de um aluno de verdade — com vínculo em {@code users.student_id}.
     * Mesma necessidade de {@code AbstractSchedulingIntegrationTest}: os
     * endpoints /me dependem do vínculo, e o {@code studentCookie} herdado do
     * auth cria usuário sem vínculo nenhum.
     */
    protected Cookie linkedTeacherCookie(long teacherId) throws Exception {
        String login = unique("billing-teacher") + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"TEACHER","teacherId":%d}
                                """.formatted(login, DEFAULT_PASSWORD, teacherId)))
                .andExpect(status().isCreated());
        return extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
    }

    protected Cookie linkedStudentCookie(long studentId) throws Exception {
        String login = unique("billing-student") + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"STUDENT","studentId":%d}
                                """.formatted(login, DEFAULT_PASSWORD, studentId)))
                .andExpect(status().isCreated());
        return extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
