package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD of the default schedule (ADMIN).
 *
 * The schedule does not store professor_id: the class's teacher is always the
 * one from the student's current link. Duplicating the teacher here would
 * create two truths that diverge at the first teacher change.
 */
class DefaultScheduleCrudIT extends AbstractSchedulingIntegrationTest {

    @Test
    void createsAndReadsSchedule() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Crud"));
        long studentId = createStudent(unique("Aluna Do Crud"), teacherId);

        long id = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(get("/default-schedules/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.time").value("07:00"));
    }

    @Test
    void updatesScheduleTime() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Troca"));
        long studentId = createStudent(unique("Aluno Da Troca"), teacherId);
        long id = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(put("/default-schedules/{id}", id)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"09:00"}
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time").value("09:00"));
    }

     /**
      * A default schedule can truly be deleted — unlike a student, it is not
      * history: it is the current configuration of the schedule.
      */
    @Test
    void deletesSchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Delete"));
        long studentId = createStudent(unique("Aluno Do Delete"), teacherId);
        long id = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(delete("/default-schedules/{id}", id).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/default-schedules/{id}", id).cookie(admin))
                .andExpect(status().isNotFound());
    }

    /**
     * Re-pointing a schedule to another student is "the schedule is leaving
     * the old owner", and the old owner's exceptions leave with it — the same
     * cleanup {@code deleteDefaultSchedule} performs. Otherwise a CANCELED
     * indexed by (student_id, date) would survive the move and silently
     * suppress the class of the schedule that student gets next.
     */
    @Test
    void movingScheduleToAnotherStudentCleansTheOldOwnersExceptions() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Mudanca"));
        long first = createStudent(unique("Aluno Antigo Da Mudanca"), teacherId);
        long second = createStudent(unique("Aluno Novo Da Mudanca"), teacherId);
        long id = createDefaultSchedule(first, "07:00");
        cancelClass(first, MONDAY);

        mockMvc.perform(put("/default-schedules/{id}", id)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"07:00"}
                                """.formatted(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(second));

        createDefaultSchedule(first, "07:00");
        assertThat(daySchedule(MONDAY).get("entries").findValues("studentId"))
                .extracting(JsonNode::asLong)
                .contains(first);
    }

    @Test
    void unknownScheduleIsNotFound() throws Exception {
        mockMvc.perform(get("/default-schedules/{id}", 999_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingIsPaginated() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Lista"));
        for (int i = 0; i < 3; i++) {
            createDefaultSchedule(createStudent(unique("Aluno Listado " + i), teacherId), "1%d:00".formatted(i));
        }

        MvcResult result = mockMvc.perform(get("/default-schedules")
                        .param("size", "2")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void rejectsScheduleWithoutTime() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Hora"));
        long studentId = createStudent(unique("Aluno Sem Hora"), teacherId);

        mockMvc.perform(post("/default-schedules")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d}
                                """.formatted(studentId)))
                .andExpect(status().isBadRequest());
    }
}
