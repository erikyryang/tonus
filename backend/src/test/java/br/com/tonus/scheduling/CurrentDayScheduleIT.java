package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem {@code ?date=}, os endpoints /me respondem sobre hoje — que é o que
 * aluno e professor querem em quase todo acesso.
 *
 * O relógio fixo da suíte ({@link SchedulingClockConfig}) marca 23h de uma
 * segunda-feira em Brasília, de propósito: nesse instante já é terça em UTC.
 * Um clock em UTC faria o aluno ver a agenda do dia seguinte toda noite — o
 * erro que só aparece depois das 21h, o pior tipo para descobrir em produção.
 */
class CurrentDayScheduleIT extends AbstractSchedulingIntegrationTest {

    @Test
    void studentScheduleWithoutDateUsesToday() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Hoje"));
        long studentId = createStudent(unique("Aluno Do Hoje"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        MvcResult result = mockMvc.perform(get("/me/schedule").cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(MONDAY))
                .andReturn();

        assertThat(studentIdsOf(json(result))).containsOnly(studentId);
    }

    @Test
    void teacherGridWithoutDateUsesToday() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Grade De Hoje"));
        long studentId = createStudent(unique("Aluna Da Grade De Hoje"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        mockMvc.perform(get("/me/grid").cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(MONDAY))
                .andExpect(jsonPath("$.slots[0].time").value("07:00"))
                .andExpect(jsonPath("$.slots[0].students[0].studentId").value(studentId));
    }

    /**
     * A data explícita continua mandando: o default é conveniência, não
     * substituição do parâmetro.
     */
    @Test
    void explicitDateStillWins() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Data Explicita"));
        long studentId = createStudent(unique("Aluno Da Data Explicita"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        mockMvc.perform(get("/me/schedule").param("date", WEDNESDAY).cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(WEDNESDAY));
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
