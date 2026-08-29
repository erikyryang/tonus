package br.com.tonus.scheduling;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who configures the schedule is the ADMIN. Students and teachers only read
 * their own slice, through the /me endpoints.
 *
 * The teacher case is the most deceptive: they own the class, but they are
 * not the ones who decide time or announcement. Letting them write here would
 * give one teacher power over another teacher's students' schedules.
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
    void teacherCannotCreateAnnouncement() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Feriado Negado"));

        mockMvc.perform(post("/announcements")
                        .cookie(linkedTeacherCookie(teacherId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-12-25","description":"Natal","suspendsClasses":true}
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
      * 401 because that is the response the project gives to a request without
      * a credential since SPEC-006 (see {@code AuthInvalidTokenIT}):
      * SecurityConfig has its own entry point. The schedule is not the place to
      * invent a convention different from the rest of the application.
      */
    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/schedule/day").param("date", MONDAY))
                .andExpect(status().isUnauthorized());
    }

     /**
      * Canceling and rescheduling is the ADMIN's job, including for their own
      * student.
      *
      * Without this coverage the {@code /schedule-exceptions} prefix was the
      * only ADMIN one without a negation test: a mistake in the rule would let
      * any authenticated student cancel any other student's class, and the
      * whole suite would stay green.
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"date":"%s","type":"CANCELED"}
                                """.formatted(studentId, MONDAY)))
                .andExpect(status().isForbidden());
    }

    private void expectForbiddenToCreateSchedule(Cookie cookie, long studentId) throws Exception {
        mockMvc.perform(post("/default-schedules")
                        .cookie(cookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"time":"07:00"}
                                """.formatted(studentId)))
                .andExpect(status().isForbidden());
    }
}
