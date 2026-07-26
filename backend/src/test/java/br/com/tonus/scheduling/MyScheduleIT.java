package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite: GET /me/schedule de um aluno nunca retorna dado de outro.
 *
 * O id do aluno vem do token e não existe caminho para informá-lo — nem path,
 * nem query, nem corpo. É o desenho que previne IDOR: não basta o endpoint
 * conferir se o id pedido é o do usuário, porque essa conferência é justamente
 * o que se esquece de fazer. Aqui não há o que conferir.
 */
class MyScheduleIT extends AbstractSchedulingIntegrationTest {

    @Test
    void studentSeesOnlyOwnClasses() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Me"));
        long mine = createStudent(unique("Aluno Dono Do Token"), teacherId);
        long other = createStudent(unique("Aluno Alheio"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");

        List<Long> visible = studentIdsOf(mySchedule(linkedStudentCookie(mine), MONDAY));

        assertThat(visible).containsOnly(mine);
    }

    /**
     * Mandar o id de outro aluno na query não muda nada: o parâmetro não existe
     * no contrato, então é ignorado — nunca vira o id consultado.
     */
    @Test
    void studentIdInQueryIsIgnored() throws Exception {
        long teacherId = createTeacher(unique("Professor Do IDOR"));
        long mine = createStudent(unique("Aluno Que Tenta"), teacherId);
        long victim = createStudent(unique("Aluno Vitima"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(victim, "08:00");

        MvcResult result = mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .param("studentId", String.valueOf(victim))
                        .cookie(linkedStudentCookie(mine)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(studentIdsOf(json(result))).containsOnly(mine);
    }

    @Test
    void cancellationRemovesTheClassFromMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Cancelado"));
        long studentId = createStudent(unique("Aluno Do Me Cancelado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        assertThat(studentIdsOf(mySchedule(student, MONDAY))).containsOnly(studentId);

        cancelClass(studentId, MONDAY);

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
    }

    @Test
    void rescheduleMovesTheClassInMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Remarcado"));
        long studentId = createStudent(unique("Aluno Do Me Remarcado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
        assertThat(studentIdsOf(mySchedule(student, WEDNESDAY))).containsOnly(studentId);
    }

    /**
     * ADMIN não tem aluno vinculado no token. Chamar um endpoint /me sem
     * vínculo é erro de uso, não uma agenda vazia — vazio esconderia o bug.
     */
    @Test
    void adminHasNoOwnSchedule() throws Exception {
        mockMvc.perform(get("/me/schedule").param("date", MONDAY).cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotUseStudentEndpoint() throws Exception {
        long teacherId = createTeacher(unique("Professor Curioso"));

        mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    private JsonNode mySchedule(Cookie student, String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/schedule").param("date", date).cookie(student))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
