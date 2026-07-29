package br.com.tonus.billing;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Assinatura é criada pela própria API (POST /subscriptions), mas fatura não
 * tem endpoint de criação — ela só nasce do job diário. Testes que precisam de
 * uma fatura existente semeiam a linha direto por SQL, no mesmo espírito de
 * {@code SchedulingEventsIT.seedStudent}: contornar a ausência de um caminho
 * HTTP que não existe por decisão de arquitetura, não por lacuna a preencher.
 */
@Import(BillingClockConfig.class)
abstract class AbstractBillingIntegrationTest extends AbstractRegistrationIntegrationTest {

    /** "Hoje" para {@link BillingClockConfig}. */
    protected static final String TODAY = "2026-07-27";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected long createSubscription(long studentId, String monthlyAmount, int dueDay) throws Exception {
        return idOf(postSubscription(studentId, monthlyAmount, dueDay)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postSubscription(long studentId, String monthlyAmount, int dueDay) throws Exception {
        return mockMvc.perform(post("/subscriptions")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":%d,"monthlyAmount":%s,"dueDay":%d}
                        """.formatted(studentId, monthlyAmount, dueDay)));
    }

    protected long seedInvoice(long subscriptionId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (subscription_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, subscriptionId, referenceMonth, amount, dueDate, status);
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
