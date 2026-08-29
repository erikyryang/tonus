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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for the billing module's tests.
 *
 * Plan and enrollment are created through the API itself, but an invoice has
 * no creation endpoint — it's only born from the daily job. Tests that need
 * an existing invoice seed the row directly via SQL, in the same spirit as
 * {@code SchedulingEventsIT.seedStudent}: working around the absence of an
 * HTTP path that doesn't exist by architecture decision, not a gap to fill.
 */
@Import(BillingClockConfig.class)
abstract class AbstractBillingIntegrationTest extends AbstractRegistrationIntegrationTest {

    /** "Today" for {@link BillingClockConfig}. */
    protected static final String TODAY = "2026-07-27";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected long createPlan(String name, String monthlyPrice) throws Exception {
        return idOf(mockMvc.perform(post("/plans")
                        .cookie(admin)
                        .with(csrf())
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
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":%d,"planId":%d}
                        """.formatted(studentId, planId)));
    }

    /** The invoice belongs to the student since SPEC-012; there's no more subscription to seed. */
    protected long seedInvoice(long studentId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, studentId, referenceMonth, amount, dueDate, status);
    }

    /**
     * Payment already registered. Serves the listing, which needs to
     * display the payment date of a PAID invoice without having gone
     * through the payment endpoint.
     */
    protected void seedPayment(long invoiceId, String paymentDate, long registeredBy) {
        jdbcTemplate.update("""
                INSERT INTO payment (invoice_id, payment_date, registered_by, created_at, updated_at)
                VALUES (?, ?::date, ?, now(), now())
                """, invoiceId, paymentDate, registeredBy);
    }

    /**
     * Cookie for a real student — linked via {@code users.student_id}. Same
     * need as {@code AbstractSchedulingIntegrationTest}: the /me endpoints
     * depend on the link, and the {@code studentCookie} inherited from auth
     * creates a user with no link at all.
     */
    protected Cookie linkedTeacherCookie(long teacherId) throws Exception {
        String login = unique("billing-teacher") + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .with(csrf())
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
                        .with(csrf())
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
