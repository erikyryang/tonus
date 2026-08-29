package br.com.tonus.registration;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whoever leaves the studio loses access.
 *
 * Today deactivating a student does not touch users, login only checks password, and there
 * is no route to deactivate a user — that is, whoever left keeps logging in
 * forever. Deactivation now publishes an event, and auth revokes.
 *
 * Revoking is automatic; regranting is manual: reactivating the student does not restore the
 * login by itself, because granting access must not be a side effect.
 */
class AccessRevocationIT extends AbstractRegistrationIntegrationTest {

    private static final String PASSWORD = "aluno-s3nha";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deactivatingStudentBlocksLogin() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Revogacao"));
        long studentId = createStudent(unique("Aluno Que Sai"), teacherId);
        String login = uniqueLogin("aluno-sai");
        createStudentUser(login, studentId);

        expectLogin(login, status().isOk());

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        expectLogin(login, status().isUnauthorized());
    }

    @Test
    void deactivatingTeacherBlocksLogin() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Sai"));
        String login = uniqueLogin("professor-sai");
        createTeacherUser(login, teacherId);

        expectLogin(login, status().isOk());

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        expectLogin(login, status().isUnauthorized());
    }

    /**
     * Blocking a new login is not enough: whoever was already logged in has a refresh token
     * valid for 7 days and would renew access on their own.
     */
    @Test
    void deactivatingStudentRevokesExistingRefreshToken() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Refresh"));
        long studentId = createStudent(unique("Aluno Com Sessao"), teacherId);
        String login = uniqueLogin("aluno-sessao");
        createStudentUser(login, studentId);

        Cookie refreshCookie = extractCookie(login(login, PASSWORD), REFRESH_COOKIE);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingStudentDoesNotRestoreLoginByItself() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Volta"));
        long studentId = createStudent(unique("Aluno Que Volta"), teacherId);
        String login = uniqueLogin("aluno-volta");
        createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        expectLogin(login, status().isUnauthorized());
    }

    @Test
    void adminRestoresAccessExplicitly() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Reconcessao"));
        long studentId = createStudent(unique("Aluno Reconcedido"), teacherId);
        String login = uniqueLogin("aluno-reconcedido");
        long userId = createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);
        expectLogin(login, status().isUnauthorized());

        mockMvc.perform(patch("/users/{id}/activate", userId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        expectLogin(login, status().isOk());
    }

    /**
     * Delivery is at-least-once, so the listener runs more than once in
     * retry scenarios and needs to be idempotent.
     */
    @Test
    void revocationIsIdempotent() throws Exception {
        long teacherId = createTeacher(unique("Professor Idempotente"));
        long studentId = createStudent(unique("Aluno Idempotente"), teacherId);
        String login = uniqueLogin("aluno-idempotente");
        createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        expectLogin(login, status().isUnauthorized());
    }

    /**
     * A student without an access user is the common case at the studio's start: the
     * listener must not blow up for finding nobody to revoke.
     */
    @Test
    void deactivatingStudentWithoutUserIsHarmless() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Login"));
        long studentId = createStudent(unique("Aluno Sem Login"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * The registry is what makes the at-least-once true: if the listener
     * had failed, the row would be left without a completion_date and the event would be
     * reprocessable instead of disappearing.
     */
    @Test
    void eventPublicationIsRecordedAndCompleted() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Registry"));
        long studentId = createStudent(unique("Aluno Do Registry"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        eventually("publicação do evento concluída", () -> Long.valueOf(0L).equals(
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM event_publication
                        WHERE event_type LIKE '%StudentDeactivated' AND completion_date IS NULL
                        """, Long.class)));

        Long completed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE '%StudentDeactivated' AND completion_date IS NOT NULL
                """, Long.class);
        assertThat(completed).isPositive();
    }

    private long createStudentUser(String login, long studentId) throws Exception {
        return createUser(login, "STUDENT", "studentId", studentId);
    }

    private long createTeacherUser(String login, long teacherId) throws Exception {
        return createUser(login, "TEACHER", "teacherId", teacherId);
    }

    private long createUser(String login, String role, String linkField, long linkId) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"%s","%s":%d}
                                """.formatted(login, PASSWORD, role, linkField, linkId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    /**
     * The revocation arrives via an asynchronous event, so the response of the
     * PATCH /deactivate returns before the auth listener has run. Waiting
     * for the database state — and not attempting login in a loop — avoids hitting the
     * rate limit of 5 attempts per minute and turning a 401 into a 429.
     */
    private void awaitAccessRevoked(String login) {
        eventually("acesso de " + login + " revogado", () -> Boolean.FALSE.equals(
                jdbcTemplate.queryForObject("SELECT active FROM users WHERE login = ?", Boolean.class, login)));
    }

    private void expectLogin(String login, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s"}
                                """.formatted(login, PASSWORD)))
                .andExpect(expected);
    }
}
