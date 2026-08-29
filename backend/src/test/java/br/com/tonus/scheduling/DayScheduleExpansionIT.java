package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A day's schedule is derived, never stored:
 *
 * <pre>
 * schedule(D) = template (if D is a weekday)
 *             − holiday on D
 *             − exceptions on D
 *             + reschedules whose new_date = D
 * </pre>
 *
 * Storing a materialized class per day would force generating rows for the
 * whole future and reconciling everything at every schedule change. Deriving
 * keeps a single source of truth.
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
      * Saturday and Sunday are empty by definition, not because of a missing
      * registered time — the template is Mon–Fri, period.
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
      * The cancellation only applies to the marked day — if it leaked into the
      * others, it would be a registration cancellation, not a class
      * cancellation.
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
      * A deactivated student leaves the schedule: whoever left the studio has
      * no class, and their default schedule stays in the table until the ADMIN
      * deletes it.
      */
    @Test
    void inactiveStudentIsNotScheduled() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aluno Inativo"));
        long studentId = createStudent(unique("Aluno Que Saiu"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
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
