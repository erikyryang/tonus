package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD do horário padrão (ADMIN).
 *
 * O horário não guarda professor_id: o professor da aula é sempre o do vínculo
 * atual do aluno. Duplicar o professor aqui criaria duas verdades que divergem
 * na primeira troca de professor.
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"09:00"}
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time").value("09:00"));
    }

    /**
     * Horário padrão pode ser apagado de verdade — diferente de aluno, não é
     * histórico: é a configuração corrente da agenda.
     */
    @Test
    void deletesSchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Delete"));
        long studentId = createStudent(unique("Aluno Do Delete"), teacherId);
        long id = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(delete("/default-schedules/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/default-schedules/{id}", id).cookie(admin))
                .andExpect(status().isNotFound());
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d}
                                """.formatted(studentId)))
                .andExpect(status().isBadRequest());
    }
}
