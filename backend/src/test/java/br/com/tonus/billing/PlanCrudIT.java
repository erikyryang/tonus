package br.com.tonus.billing;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O catálogo de planos do estúdio (SPEC-012).
 *
 * Não existe DELETE, mesma razão de aluno e professor: plano desativado some
 * das listagens e das novas matrículas, mas continua explicando as faturas que
 * já foram emitidas com ele.
 */
class PlanCrudIT extends AbstractBillingIntegrationTest {

    @Test
    void createsAndListsPlan() throws Exception {
        String name = unique("Musculação");
        long id = createPlan(name, "350.00");

        MvcResult result = mockMvc.perform(get("/plans").param("size", "100").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).anySatisfy(node -> {
            assertThat(node.get("id").asLong()).isEqualTo(id);
            assertThat(node.get("name").asText()).isEqualTo(name);
            assertThat(node.get("monthlyPrice").asDouble()).isEqualTo(350.00);
            assertThat(node.get("active").asBoolean()).isTrue();
        });
    }

    @Test
    void updatesNameAndPrice() throws Exception {
        long id = createPlan(unique("Pilates"), "300.00");

        mockMvc.perform(put("/plans/{id}", id)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pilates Premium","monthlyPrice":"420.00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pilates Premium"))
                .andExpect(jsonPath("$.monthlyPrice").value(420.00));
    }

    @Test
    void activatesAndDeactivates() throws Exception {
        long id = createPlan(unique("Sazonal"), "200.00");

        mockMvc.perform(patch("/plans/{id}/deactivate", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/plans/{id}/activate", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Desativar plano com matrícula ativa é permitido (decisão da SPEC-012):
     * encerrar um plano no fim do ano não pode exigir desmatricular aluno por
     * aluno. Quem avisa do estrago é a tela, com a contagem de afetados.
     */
    @Test
    void deactivatingPlanWithActiveEnrollmentIsAllowed() throws Exception {
        long planId = createPlan(unique("Plano Que Acaba"), "250.00");
        long studentId = createStudent(unique("Aluno Do Plano Que Acaba"),
                createTeacher(unique("Professor Do Plano Que Acaba")));
        enroll(studentId, planId);

        mockMvc.perform(patch("/plans/{id}/deactivate", planId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /** Quantos alunos perdem a cobrança: é o que a tela mostra antes de confirmar. */
    @Test
    void listExposesHowManyStudentsAreEnrolled() throws Exception {
        long planId = createPlan(unique("Plano Cheio"), "250.00");
        long teacherId = createTeacher(unique("Professor Do Plano Cheio"));
        enroll(createStudent(unique("Aluno Cheio Um"), teacherId), planId);
        enroll(createStudent(unique("Aluno Cheio Dois"), teacherId), planId);

        mockMvc.perform(get("/plans/{id}", planId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEnrollments").value(2));
    }

    @Test
    void listFiltersByActive() throws Exception {
        long active = createPlan(unique("Ativo"), "100.00");
        long inactive = createPlan(unique("Inativo"), "110.00");
        mockMvc.perform(patch("/plans/{id}/deactivate", inactive).cookie(admin))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/plans")
                        .param("active", "true")
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(result).get("content").findValues("id"))
                .extracting(JsonNode::asLong)
                .contains(active)
                .doesNotContain(inactive);
    }

    @Test
    void rejectsNonPositivePrice() throws Exception {
        postPlan(unique("Grátis"), "0.00").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankName() throws Exception {
        postPlan("", "100.00").andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotManagePlans() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Acesso A Planos"));

        mockMvc.perform(get("/plans").cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    private ResultActions postPlan(String name, String monthlyPrice) throws Exception {
        return mockMvc.perform(post("/plans")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","monthlyPrice":"%s"}
                        """.formatted(name, monthlyPrice)));
    }
}
