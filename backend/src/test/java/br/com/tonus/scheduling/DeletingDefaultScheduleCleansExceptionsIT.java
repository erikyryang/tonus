package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting the default schedule also deletes the student's exceptions.
 *
 * {@code schedule_exception} is indexed by (student_id, date), without any
 * link to the id of the schedule that originated it. Without the cleanup, an
 * exception survives the schedule: the ADMIN deletes the schedule of a student
 * who had a canceled Monday, creates a new schedule for the same student also
 * on a Monday, and the old exception starts suppressing the class again — in
 * silence, because nothing in the expansion knows that that cancellation came
 * from a schedule that no longer exists.
 */
class DeletingDefaultScheduleCleansExceptionsIT extends AbstractSchedulingIntegrationTest {

    @Test
    void deletingScheduleRemovesStudentExceptions() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Limpeza"));
        long studentId = createStudent(unique("Aluno Da Limpeza"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, MONDAY);

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(exceptionsOf(studentId)).isEmpty();
    }

     /**
      * The scenario that motivated the cleanup: an orphan exception cannot
      * survive the schedule and then suppress a new schedule created on the
      * same day.
      */
    @Test
    void oldCancellationDoesNotSuppressANewScheduleOnTheSameDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Ressurreicao"));
        long studentId = createStudent(unique("Aluno Da Ressurreicao"), teacherId);
        long oldScheduleId = createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, MONDAY);

        mockMvc.perform(delete("/default-schedules/{id}", oldScheduleId).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());
        createDefaultSchedule(studentId, "09:00");

        assertThat(studentIdsOf(daySchedule(MONDAY))).contains(studentId);
    }

     /**
      * The cleanup is complete, not selective: reschedules disappear too.
      * Deleting the schedule returns the student fully to "sem agenda", instead
      * of deciding case by case what one-off survives the deletion of the
      * recurring one.
      */
    @Test
    void deletingScheduleAlsoRemovesRescheduledClass() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Remarcacao Apagada"));
        long studentId = createStudent(unique("Aluno Da Remarcacao Apagada"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");
        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(studentIdsOf(daySchedule(WEDNESDAY))).doesNotContain(studentId);
    }

     /**
      * A student with no exceptions is the common case: deleting the schedule
      * must not blow up for having nothing to clean.
      */
    @Test
    void deletingScheduleWithoutExceptionsIsHarmless() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Excecao"));
        long studentId = createStudent(unique("Aluno Sem Excecao"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private JsonNode exceptionsOf(long studentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/schedule-exceptions")
                        .param("studentId", String.valueOf(studentId))
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("content");
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
