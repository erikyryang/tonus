package br.com.tonus.billing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.StreamSupport;
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

    /**
     * SPEC-008: a tela de faturas é lida por nome de aluno e, na fatura PAGA,
     * pela data em que o dinheiro entrou. Resolver os dois no front custaria uma
     * chamada por linha — quem sabe juntar isso é o back.
     */
    @Test
    void listingCarriesStudentNameAndPaymentDate() throws Exception {
        String name = unique("Aluno Nomeado");
        long studentId = createStudent(name, createTeacher(unique("Professor Nomeado")));
        long subscriptionId = createSubscription(studentId, "200.00", 10);
        long openId = seedInvoice(subscriptionId, "2026-07-01", "200.00", "2026-07-10", "OPEN");
        long paidId = seedInvoice(subscriptionId, "2026-06-01", "200.00", "2026-06-10", "PAID");
        // registered_by não tem FK (billing não pode depender de auth): id solto basta.
        seedPayment(paidId, "2026-06-08", 1L);

        MvcResult result = mockMvc.perform(get("/invoices").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");

        JsonNode open = nodeWithId(content, openId);
        assertThat(open.get("studentName").asText()).isEqualTo(name);
        assertThat(open.get("referenceMonth").asText()).isEqualTo("2026-07");
        assertThat(open.get("paymentDate").isNull()).isTrue();

        JsonNode paid = nodeWithId(content, paidId);
        assertThat(paid.get("studentName").asText()).isEqualTo(name);
        assertThat(paid.get("paymentDate").asText()).isEqualTo("2026-06-08");
    }

    private static JsonNode nodeWithId(JsonNode content, long id) {
        return StreamSupport.stream(content.spliterator(), false)
                .filter(node -> node.get("id").asLong() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fatura " + id + " não veio na listagem"));
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
