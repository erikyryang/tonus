package br.com.tonus.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /me/invoices: o id do aluno vem do token, nunca de path ou query — mesmo
 * padrão de {@code MyScheduleController}. Não existe parâmetro de id, então
 * IDOR não é algo que alguém precisa lembrar de prevenir.
 */
class MyInvoicesIT extends AbstractBillingIntegrationTest {

    @Test
    void studentSeesOnlyOwnInvoices() throws Exception {
        long teacherId = createTeacher(unique("Professor Das Faturas"));
        long ownStudentId = createStudent(unique("Aluno Dono Da Fatura"), teacherId);
        long otherStudentId = createStudent(unique("Aluno De Fora"), teacherId);

        long ownSubscriptionId = createSubscription(ownStudentId, "200.00", 10);
        long otherSubscriptionId = createSubscription(otherStudentId, "200.00", 10);
        long ownInvoiceId = seedInvoice(ownSubscriptionId, "2026-07-01", "200.00", "2026-07-10", "OPEN");
        seedInvoice(otherSubscriptionId, "2026-07-01", "200.00", "2026-07-10", "OPEN");

        MvcResult result = mockMvc.perform(get("/me/invoices").cookie(linkedStudentCookie(ownStudentId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).extracting(node -> node.get("id").asLong()).containsExactly(ownInvoiceId);
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
