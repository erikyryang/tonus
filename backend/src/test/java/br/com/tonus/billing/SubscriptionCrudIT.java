package br.com.tonus.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionCrudIT extends AbstractBillingIntegrationTest {

    @Test
    void createsAndListsSubscription() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Assinatura"));
        long studentId = createStudent(unique("Aluno Da Assinatura"), teacherId);

        long id = createSubscription(studentId, "250.00", 10);

        MvcResult result = mockMvc.perform(get("/subscriptions").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = json(result).get("content");
        assertThat(content).anySatisfy(node -> assertThat(node.get("id").asLong()).isEqualTo(id));
    }

    @Test
    void getsSubscriptionById() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Get"));
        long studentId = createStudent(unique("Aluno Do Get"), teacherId);
        long id = createSubscription(studentId, "250.00", 10);

        mockMvc.perform(get("/subscriptions/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.studentId").value(studentId));
    }

    @Test
    void gettingUnknownSubscriptionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/subscriptions/{id}", 999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesSubscription() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Troca"));
        long studentId = createStudent(unique("Aluno Da Troca"), teacherId);
        long id = createSubscription(studentId, "200.00", 5);

        mockMvc.perform(put("/subscriptions/{id}", id)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"monthlyAmount":"300.00","dueDay":20}
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyAmount").value(300.00))
                .andExpect(jsonPath("$.dueDay").value(20));
    }

    @Test
    void activatesAndDeactivatesSubscription() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Ciclo"));
        long studentId = createStudent(unique("Aluno Do Ciclo"), teacherId);
        long id = createSubscription(studentId, "200.00", 5);

        mockMvc.perform(patch("/subscriptions/{id}/deactivate", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/subscriptions/{id}/activate", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Um aluno tem no máximo uma assinatura: é a idempotência de "quem cobrar"
     * expressa no banco (uk_subscription_student), não só validação na API.
     */
    @Test
    void secondSubscriptionForSameStudentIsRejected() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Duplicata"));
        long studentId = createStudent(unique("Aluno Duplicado"), teacherId);
        createSubscription(studentId, "200.00", 5);

        postSubscription(studentId, "999.00", 15)
                .andExpect(status().isConflict());
    }

    @Test
    void unknownStudentIsRejected() throws Exception {
        postSubscription(999_999L, "200.00", 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveStudentIsRejected() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Inativo"));
        long studentId = createStudent(unique("Aluno Inativo Da Cobranca"), teacherId);
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        postSubscription(studentId, "200.00", 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveMonthlyAmountIsRejected() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Valor Invalido"));
        long studentId = createStudent(unique("Aluno Do Valor Invalido"), teacherId);

        postSubscription(studentId, "0.00", 5)
                .andExpect(status().isBadRequest());
    }

    /**
     * Reativar não pode devolver cobrança a quem já saiu do estúdio — mesma
     * regra de RegistrationService.activateStudent (exige professor ativo).
     * Sem isso, a assinatura reativada voltaria a gerar fatura para um aluno
     * inativo no primeiro job diário.
     */
    @Test
    void reactivatingSubscriptionOfInactiveStudentIsRejected() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Reativacao"));
        long studentId = createStudent(unique("Aluno Da Reativacao"), teacherId);
        long id = createSubscription(studentId, "200.00", 5);
        mockMvc.perform(patch("/subscriptions/{id}/deactivate", id).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/subscriptions/{id}/activate", id).cookie(admin))
                .andExpect(status().isBadRequest());
    }
}
