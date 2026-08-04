package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apagar o horário padrão apaga junto as exceções do aluno.
 *
 * {@code schedule_exception} é indexada por (student_id, date), sem nenhum
 * vínculo com o id do horário que a originou. Sem a limpeza, uma exceção
 * sobrevive ao horário: o ADMIN apaga o horário de um aluno que tinha uma
 * segunda cancelada, cria um horário novo para o mesmo aluno também numa
 * segunda, e a exceção antiga volta a suprimir a aula — em silêncio, porque
 * nada na expansão sabe que aquele cancelamento era de um horário que já não
 * existe mais.
 */
class DeletingDefaultScheduleCleansExceptionsIT extends AbstractSchedulingIntegrationTest {

    @Test
    void deletingScheduleRemovesStudentExceptions() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Limpeza"));
        long studentId = createStudent(unique("Aluno Da Limpeza"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, MONDAY);

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(exceptionsOf(studentId)).isEmpty();
    }

    /**
     * O cenário que motivou a limpeza: uma exceção órfã não pode sobreviver
     * ao horário para depois suprimir um horário novo criado no mesmo dia.
     */
    @Test
    void oldCancellationDoesNotSuppressANewScheduleOnTheSameDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Ressurreicao"));
        long studentId = createStudent(unique("Aluno Da Ressurreicao"), teacherId);
        long oldScheduleId = createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, MONDAY);

        mockMvc.perform(delete("/default-schedules/{id}", oldScheduleId).cookie(admin))
                .andExpect(status().isNoContent());
        createDefaultSchedule(studentId, "09:00");

        assertThat(studentIdsOf(daySchedule(MONDAY))).contains(studentId);
    }

    /**
     * A limpeza é de tudo, não seletiva: remarcação também some. Apagar o
     * horário devolve o aluno a "sem agenda" por inteiro, em vez de decidir
     * caso a caso o que de pontual sobrevive à exclusão do recorrente.
     */
    @Test
    void deletingScheduleAlsoRemovesRescheduledClass() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Remarcacao Apagada"));
        long studentId = createStudent(unique("Aluno Da Remarcacao Apagada"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");
        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(studentIdsOf(daySchedule(WEDNESDAY))).doesNotContain(studentId);
    }

    /**
     * Aluno sem exceção nenhuma é o caso comum: apagar o horário não pode
     * explodir por não ter nada para limpar.
     */
    @Test
    void deletingScheduleWithoutExceptionsIsHarmless() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Excecao"));
        long studentId = createStudent(unique("Aluno Sem Excecao"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin))
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
