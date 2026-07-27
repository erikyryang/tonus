package br.com.tonus.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceListingIT extends AbstractBillingIntegrationTest {

    @Test
    void listsInvoicesFilteredByStatus() throws Exception {
        long studentId = createStudent(unique("Aluno Da Listagem"), createTeacher(unique("Professor Da Listagem")));
        long subscriptionId = createSubscription(studentId, "200.00", 10);
        long openId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-10", "OPEN");
        seedInvoice(subscriptionId, "2026-06-01", "200.00", "2026-06-10", "PAID");

        MvcResult result = mockMvc.perform(get("/invoices")
                        .param("status", "OPEN")
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).anySatisfy(node -> assertThat(node.get("id").asLong()).isEqualTo(openId));
        assertThat(content).allSatisfy(node -> assertThat(node.get("status").asText()).isEqualTo("OPEN"));
    }

    @Test
    void listingIsPaged() throws Exception {
        long studentId = createStudent(unique("Aluno Paginado"), createTeacher(unique("Professor Paginado")));
        long subscriptionId = createSubscription(studentId, "200.00", 10);
        seedInvoice(subscriptionId, "2026-05-01", "200.00", "2026-05-10", "PAID");
        seedInvoice(subscriptionId, "2026-06-01", "200.00", "2026-06-10", "PAID");
        seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-10", "OPEN");

        mockMvc.perform(get("/invoices").param("page", "0").param("size", "2").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
    }
}
