package br.com.tonus.scheduling;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem configura a agenda é o ADMIN. Aluno e professor só leem a própria fatia,
 * pelos endpoints /me.
 *
 * O caso do professor é o que mais engana: ele é o dono da aula, mas não é
 * quem decide horário nem feriado. Deixá-lo escrever aqui daria a um professor
 * poder sobre a agenda dos alunos de outro.
 */
class SchedulingAccessControlIT extends AbstractSchedulingIntegrationTest {

    @Test
    void studentCannotCreateSchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Acesso"));
        long studentId = createStudent(unique("Aluno Sem Poder"), teacherId);

        expectForbiddenToCreateSchedule(linkedStudentCookie(studentId), studentId);
    }

    @Test
    void teacherCannotCreateSchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Poder"));
        long studentId = createStudent(unique("Aluno Do Professor Sem Poder"), teacherId);

        expectForbiddenToCreateSchedule(linkedTeacherCookie(teacherId), studentId);
    }

    @Test
    void teacherCannotCreateHoliday() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Feriado Negado"));

        mockMvc.perform(post("/holidays")
                        .cookie(linkedTeacherCookie(teacherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-12-25","description":"Natal"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotSeeTheWholeDaySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Agenda Fechada"));
        long studentId = createStudent(unique("Aluno Sem Visao Geral"), teacherId);

        mockMvc.perform(get("/schedule/day")
                        .param("date", MONDAY)
                        .cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotSeeTheWholeDaySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Visao Geral"));

        mockMvc.perform(get("/schedule/day")
                        .param("date", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    /**
     * 401 porque é a resposta que o projeto dá a requisição sem credencial
     * desde a SPEC-006 (ver {@code AuthInvalidTokenIT}): o SecurityConfig tem
     * entry point próprio. A agenda não é lugar de inventar convenção diferente
     * do resto da aplicação.
     */
    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/schedule/day").param("date", MONDAY))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Cancelar e remarcar é do ADMIN, inclusive para o próprio aluno.
     *
     * Sem esta cobertura o prefixo {@code /schedule-exceptions} era o único de
     * ADMIN sem teste de negação: um engano na regra deixaria qualquer aluno
     * autenticado cancelando a aula de qualquer outro, e a suíte inteira
     * continuaria verde.
     */
    @Test
    void studentCannotCancelAnyClass() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Excecao Negada"));
        long studentId = createStudent(unique("Aluno Que Nao Cancela"), teacherId);

        expectForbiddenToCancel(linkedStudentCookie(studentId), studentId);
    }

    @Test
    void teacherCannotCancelAnyClass() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Nao Cancela"));
        long studentId = createStudent(unique("Aluno Do Professor Que Nao Cancela"), teacherId);

        expectForbiddenToCancel(linkedTeacherCookie(teacherId), studentId);
    }

    @Test
    void studentCannotListExceptions() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Listagem Negada"));
        long studentId = createStudent(unique("Aluno Sem Listagem"), teacherId);

        mockMvc.perform(get("/schedule-exceptions").cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isForbidden());
    }

    private void expectForbiddenToCancel(Cookie cookie, long studentId) throws Exception {
        mockMvc.perform(post("/schedule-exceptions")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"date":"%s","type":"CANCELED"}
                                """.formatted(studentId, MONDAY)))
                .andExpect(status().isForbidden());
    }

    private void expectForbiddenToCreateSchedule(Cookie cookie, long studentId) throws Exception {
        mockMvc.perform(post("/default-schedules")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"07:00"}
                                """.formatted(studentId)))
                .andExpect(status().isForbidden());
    }
}
