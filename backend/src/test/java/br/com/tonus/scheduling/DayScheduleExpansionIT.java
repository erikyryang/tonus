package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A agenda de um dia é derivada, nunca armazenada:
 *
 * <pre>
 * agenda(D) = template (se D é dia útil)
 *           − feriado em D
 *           − exceções em D
 *           + remarcações cuja nova_data = D
 * </pre>
 *
 * Guardar aula materializada por dia obrigaria a gerar linhas para o futuro
 * inteiro e a reconciliar tudo a cada troca de horário. Derivar mantém uma
 * verdade só.
 */
class DayScheduleExpansionIT extends AbstractSchedulingIntegrationTest {

    @Test
    void weekdayShowsTemplate() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Template"));
        String name = unique("Aluna Do Template");
        long studentId = createStudent(name, teacherId);
        createDefaultSchedule(studentId, "07:00");

        JsonNode entry = entryOf(daySchedule(MONDAY), studentId);

        assertThat(entry.get("time").asText()).isEqualTo("07:00");
        assertThat(entry.get("studentName").asText()).isEqualTo(name);
        assertThat(entry.get("teacherId").asLong()).isEqualTo(teacherId);
        assertThat(entry.get("origin").asText()).isEqualTo("TEMPLATE");
    }

    /**
     * Sábado e domingo são vazios por definição, não por falta de horário
     * cadastrado — o template é seg–sex e ponto.
     */
    @Test
    void weekendIsEmptyByDefinition() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Sabado"));
        long studentId = createStudent(unique("Aluno Do Sabado"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        assertThat(studentIdsOf(daySchedule(SATURDAY))).doesNotContain(studentId);
    }

    @Test
    void canceledClassDisappearsFromTheDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Cancelamento"));
        long studentId = createStudent(unique("Aluno Que Faltou"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        assertThat(studentIdsOf(daySchedule(MONDAY))).contains(studentId);

        cancelClass(studentId, MONDAY);

        assertThat(studentIdsOf(daySchedule(MONDAY))).doesNotContain(studentId);
    }

    /**
     * O cancelamento vale só para o dia marcado — se vazasse para os demais,
     * seria um cancelamento de matrícula, não de aula.
     */
    @Test
    void cancellationAffectsOnlyItsOwnDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Dia Seguinte"));
        long studentId = createStudent(unique("Aluno De Um Dia So"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        cancelClass(studentId, MONDAY);

        assertThat(studentIdsOf(daySchedule(TUESDAY))).contains(studentId);
    }

    /**
     * Aluno desativado sai da agenda: quem saiu do estúdio não tem aula, e o
     * horário padrão dele continua na tabela até o ADMIN apagar.
     */
    @Test
    void inactiveStudentIsNotScheduled() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aluno Inativo"));
        long studentId = createStudent(unique("Aluno Que Saiu"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        assertThat(studentIdsOf(daySchedule(MONDAY))).doesNotContain(studentId);
    }

    @Test
    void dayScheduleRequiresDate() throws Exception {
        mockMvc.perform(get("/schedule/day").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    private JsonNode entryOf(JsonNode day, long studentId) {
        for (JsonNode entry : day.get("entries")) {
            if (entry.get("studentId").asLong() == studentId) {
                return entry;
            }
        }
        throw new AssertionError("Aluno %d não está na agenda de %s".formatted(studentId, day.get("date").asText()));
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
