package br.com.tonus.billing;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O vínculo aluno → plano (SPEC-012). O aluno pode ter N planos; o mesmo plano,
 * uma vez só.
 *
 * O caminho é `/plan-enrollments`, e não `/students/{id}/plans`: o vínculo é de
 * billing, e pendurá-lo sob `/students` sugeriria que ele é do registration —
 * que não conhece cobrança nenhuma.
 */
class PlanEnrollmentIT extends AbstractBillingIntegrationTest {

    @Test
    void enrollsStudentInSeveralPlans() throws Exception {
        long studentId = createStudent(unique("Aluno De Dois Planos"),
                createTeacher(unique("Professor De Dois Planos")));
        long musculacao = createPlan(unique("Musculação"), "350.00");
        long pilates = createPlan(unique("Pilates"), "270.00");

        enroll(studentId, musculacao);
        enroll(studentId, pilates);

        assertThat(planIdsOf(studentId)).containsExactlyInAnyOrder(musculacao, pilates);
    }

    /**
     * O mesmo plano duas vezes tornaria ambíguo quanto cobrar. Para cobrar em
     * dobro, o caminho é um plano de outro preço.
     */
    @Test
    void sameStudentAndPlanTwiceIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Duplicado"), createTeacher(unique("Professor Duplicado")));
        long planId = createPlan(unique("Plano Único"), "350.00");
        enroll(studentId, planId);

        postEnrollment(studentId, planId).andExpect(status().isConflict());
    }

    /** A regra é do banco também, não só da API. */
    @Test
    void databaseRejectsDuplicatedEnrollment() throws Exception {
        long studentId = createStudent(unique("Aluno Do Banco"), createTeacher(unique("Professor Do Banco")));
        long planId = createPlan(unique("Plano Do Banco"), "350.00");
        enroll(studentId, planId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at)
                VALUES (?, ?, TRUE, now(), now())
                """, studentId, planId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deactivatesAndReactivatesEnrollment() throws Exception {
        long studentId = createStudent(unique("Aluno Que Pausa"), createTeacher(unique("Professor Que Pausa")));
        long planId = createPlan(unique("Plano Que Pausa"), "350.00");
        long enrollmentId = enroll(studentId, planId);

        mockMvc.perform(patch("/plan-enrollments/{id}/deactivate", enrollmentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/plan-enrollments/{id}/activate", enrollmentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /** Matricular quem não existe é erro de quem chama, não 500. */
    @Test
    void unknownStudentIsRejected() throws Exception {
        long planId = createPlan(unique("Plano Sem Aluno"), "350.00");

        postEnrollment(999_999L, planId).andExpect(status().isBadRequest());
    }

    @Test
    void inactiveStudentIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Inativo Do Plano"),
                createTeacher(unique("Professor Do Inativo")));
        long planId = createPlan(unique("Plano Do Inativo"), "350.00");
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        postEnrollment(studentId, planId).andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlanIsRejected() throws Exception {
        long studentId = createStudent(unique("Aluno Sem Plano"), createTeacher(unique("Professor Sem Plano")));

        postEnrollment(studentId, 999_999L).andExpect(status().isBadRequest());
    }

    /**
     * A listagem responde "quais são os planos deste aluno", que é a pergunta
     * da tela do aluno — e a única pergunta que ela faz.
     */
    @Test
    void listsOnlyTheEnrollmentsOfTheAskedStudent() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Filtro"));
        long mine = createStudent(unique("Aluno Do Filtro"), teacherId);
        long other = createStudent(unique("Aluno Alheio Do Filtro"), teacherId);
        long planId = createPlan(unique("Plano Do Filtro"), "350.00");
        enroll(mine, planId);
        enroll(other, planId);

        assertThat(planIdsOf(mine)).containsExactly(planId);
        assertThat(enrollments(mine).findValues("studentId"))
                .allSatisfy(node -> assertThat(node.asLong()).isEqualTo(mine));
    }

    private JsonNode enrollments(long studentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/plan-enrollments")
                        .param("studentId", String.valueOf(studentId))
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private java.util.List<Long> planIdsOf(long studentId) throws Exception {
        return enrollments(studentId).findValues("planId").stream().map(JsonNode::asLong).toList();
    }
}
