package br.com.tonus.billing;

import tools.jackson.databind.JsonNode;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /me/invoices: the student id comes from the token, never from path or
 * query — same pattern as {@code MyScheduleController}. There is no id
 * parameter, so IDOR is not something anyone needs to remember to prevent.
 */
class MyInvoicesIT extends AbstractBillingIntegrationTest {

    @Test
    void studentSeesOnlyOwnInvoices() throws Exception {
        long teacherId = createTeacher(unique("Professor Das Faturas"));
        long ownStudentId = createStudent(unique("Aluno Dono Da Fatura"), teacherId);
        long otherStudentId = createStudent(unique("Aluno De Fora"), teacherId);

        long ownInvoiceId = seedInvoice(ownStudentId, "2026-07-01", "200.00", "2026-07-10", "OPEN");
        seedInvoice(otherStudentId, "2026-07-01", "200.00", "2026-07-10", "OPEN");

        MvcResult result = mockMvc.perform(get("/me/invoices").cookie(linkedStudentCookie(ownStudentId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).extracting(node -> node.get("id").asLong()).containsExactly(ownInvoiceId);
    }

    /**
     * The student reads the same response as the ADMIN, and the write-off date
     * is what they look for there most — it is their receipt. The SPEC-008
     * batch changed this path too, so it has its own assertion.
     */
    @Test
    void studentSeesPaymentDateOfSettledInvoice() throws Exception {
        String name = unique("Aluno Com Recibo");
        long studentId = createStudent(name, createTeacher(unique("Professor Do Recibo")));
        long openId = seedInvoice(studentId, "2026-07-01", "200.00", "2026-07-10", "OPEN");
        long paidId = seedInvoice(studentId, "2026-06-01", "200.00", "2026-06-10", "PAID");
        seedPayment(paidId, "2026-06-08", 1L);

        MvcResult result = mockMvc.perform(get("/me/invoices").param("size", "100")
                        .cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).allSatisfy(node -> assertThat(node.get("studentName").asText()).isEqualTo(name));
        assertThat(nodeWithId(content, paidId).get("paymentDate").asText()).isEqualTo("2026-06-08");
        assertThat(nodeWithId(content, openId).get("paymentDate").isNull()).isTrue();
    }

    private static JsonNode nodeWithId(JsonNode content, long id) {
        return StreamSupport.stream(content.spliterator(), false)
                .filter(node -> node.get("id").asLong() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fatura " + id + " não veio na listagem"));
    }

    @Test
    void adminCannotUseMeInvoices() throws Exception {
        mockMvc.perform(get("/me/invoices").cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/me/invoices"))
                .andExpect(status().isUnauthorized());
    }
}
