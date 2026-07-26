package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ler e desfazer exceção.
 *
 * O DELETE é o que fecha o ciclo: cancelamento marcado por engano precisa ter
 * como voltar atrás pela API, senão a correção só existe no banco — e aí quem
 * corrige é quem tem acesso ao banco, não quem administra o estúdio.
 */
class ScheduleExceptionCrudIT extends AbstractSchedulingIntegrationTest {

    @Test
    void readsExceptionById() throws Exception {
        long studentId = seedStudent("Aluno Da Leitura");
        long id = cancelClass(studentId, MONDAY);

        mockMvc.perform(get("/schedule-exceptions/{id}", id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.date").value(MONDAY))
                .andExpect(jsonPath("$.type").value("CANCELED"));
    }

    @Test
    void unknownExceptionIsNotFound() throws Exception {
        mockMvc.perform(get("/schedule-exceptions/{id}", 999_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    /**
     * Apagar a exceção devolve o aluno à agenda daquele dia — é a prova de que
     * a expansão volta a considerar o template, e não que sobrou algum estado.
     */
    @Test
    void deletingCancellationRestoresTheClass() throws Exception {
        long studentId = seedStudent("Aluno Que Volta A Ter Aula");
        createDefaultSchedule(studentId, "07:00");
        long id = cancelClass(studentId, MONDAY);

        assertThat(studentIdsOf(daySchedule(MONDAY))).doesNotContain(studentId);

        mockMvc.perform(delete("/schedule-exceptions/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(studentIdsOf(daySchedule(MONDAY))).contains(studentId);
    }

    /**
     * Apagar a remarcação tira a aula extra da data de destino e devolve a
     * original — se as duas ficassem, o aluno teria aula nos dois dias.
     *
     * A asserção é sobre a origem, não sobre a presença: quarta é dia útil, e o
     * aluno continua tendo aula lá pelo template. O que não pode sobrar é a
     * aula RESCHEDULED.
     */
    @Test
    void deletingRescheduleRemovesTheClassFromTheNewDate() throws Exception {
        long studentId = seedStudent("Aluno Da Remarcacao Desfeita");
        createDefaultSchedule(studentId, "07:00");
        long id = rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(originsOf(daySchedule(WEDNESDAY), studentId)).contains("RESCHEDULED");

        mockMvc.perform(delete("/schedule-exceptions/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(originsOf(daySchedule(WEDNESDAY), studentId)).containsOnly("TEMPLATE");
        assertThat(studentIdsOf(daySchedule(MONDAY))).contains(studentId);
    }

    /**
     * Apagada a exceção, o par (aluno, dia) fica livre de novo — a UNIQUE não
     * pode continuar valendo para uma linha que não existe mais.
     */
    @Test
    void deletingFreesTheStudentDayForANewException() throws Exception {
        long studentId = seedStudent("Aluno Da Segunda Chance");
        long id = cancelClass(studentId, MONDAY);

        mockMvc.perform(delete("/schedule-exceptions/{id}", id).cookie(admin))
                .andExpect(status().isNoContent());

        cancelClass(studentId, MONDAY);
    }

    @Test
    void deletingUnknownExceptionIsNotFound() throws Exception {
        mockMvc.perform(delete("/schedule-exceptions/{id}", 999_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStudent() throws Exception {
        long mine = seedStudent("Aluno Filtrado");
        long other = seedStudent("Aluno Nao Filtrado");
        cancelClass(mine, MONDAY);
        cancelClass(other, MONDAY);

        MvcResult result = mockMvc.perform(get("/schedule-exceptions")
                        .param("studentId", String.valueOf(mine))
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(contentStudentIds(json(result))).containsOnly(mine);
    }

    @Test
    void listFiltersByDateRange() throws Exception {
        long studentId = seedStudent("Aluno Do Intervalo");
        cancelClass(studentId, MONDAY);
        cancelClass(studentId, WEDNESDAY);

        MvcResult result = mockMvc.perform(get("/schedule-exceptions")
                        .param("studentId", String.valueOf(studentId))
                        .param("from", TUESDAY)
                        .param("to", WEDNESDAY)
                        .param("size", "100")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("date").asText()).isEqualTo(WEDNESDAY);
    }

    @Test
    void listIsPaginated() throws Exception {
        long studentId = seedStudent("Aluno Da Pagina");
        cancelClass(studentId, MONDAY);
        cancelClass(studentId, TUESDAY);
        cancelClass(studentId, WEDNESDAY);

        MvcResult result = mockMvc.perform(get("/schedule-exceptions")
                        .param("studentId", String.valueOf(studentId))
                        .param("size", "2")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body.get("content")).hasSize(2);
        assertThat(body.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void studentCannotListExceptions() throws Exception {
        long studentId = seedStudent("Aluno Sem Listagem");

        mockMvc.perform(get("/schedule-exceptions").cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isForbidden());
    }

    private long seedStudent(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor Do Crud De Excecao")));
    }

    private List<Long> contentStudentIds(JsonNode page) {
        return page.get("content").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }

    /** As origens das aulas daquele aluno no dia — pode haver mais de uma. */
    private List<String> originsOf(JsonNode day, long studentId) {
        List<String> origins = new ArrayList<>();
        for (JsonNode entry : day.get("entries")) {
            if (entry.get("studentId").asLong() == studentId) {
                origins.add(entry.get("origin").asText());
            }
        }
        return origins;
    }
}
