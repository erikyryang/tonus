package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta usada pela coluna "Horário" de /students (SPEC-009, mudança 6/7).
 *
 * O grafo não muda: registration não passa a consultar scheduling. É o
 * frontend que cruza esta lista com a tabela de alunos que já tem em mãos.
 */
class DefaultScheduleByStudentIT extends AbstractSchedulingIntegrationTest {

    @Test
    void mapsScheduledStudentsAndOmitsTheRest() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Mapa"));
        long withSchedule = createStudent(unique("Aluna Com Horario"), teacherId);
        long withoutSchedule = createStudent(unique("Aluno Sem Horario"), teacherId);
        createDefaultSchedule(withSchedule, "07:00");

        mockMvc.perform(get("/default-schedules/by-student").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['%d']".formatted(withSchedule)).value("07:00"))
                .andExpect(jsonPath("$['%d']".formatted(withoutSchedule)).doesNotExist());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/default-schedules/by-student"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapsEveryScheduledStudentAtOnce() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Mapa Cheio"));
        long first = createStudent(unique("Primeira Aluna"), teacherId);
        long second = createStudent(unique("Segundo Aluno"), teacherId);
        createDefaultSchedule(first, "07:00");
        createDefaultSchedule(second, "18:30");

        mockMvc.perform(get("/default-schedules/by-student").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['%d']".formatted(first)).value("07:00"))
                .andExpect(jsonPath("$['%d']".formatted(second)).value("18:30"));
    }

    /** Apagar o horário devolve o aluno a "sem agenda" — some do mapa também. */
    @Test
    void deletingScheduleRemovesStudentFromMap() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Apagado"));
        long studentId = createStudent(unique("Aluno Que Some Do Mapa"), teacherId);
        long scheduleId = createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(delete("/default-schedules/{id}", scheduleId).cookie(admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/default-schedules/by-student").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['%d']".formatted(studentId)).doesNotExist());
    }
}
