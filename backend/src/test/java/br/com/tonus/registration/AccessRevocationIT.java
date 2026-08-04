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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem sai do estúdio perde o acesso.
 *
 * Hoje desativar um aluno não toca em users, o login só confere senha, e não
 * existe rota para desativar usuário — ou seja, quem saiu continua entrando
 * para sempre. A desativação passa a publicar evento, e o auth revoga.
 *
 * Revogar é automático; reconceder é manual: reativar o aluno não devolve o
 * login sozinho, porque conceder acesso não deve ser efeito colateral.
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

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
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

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        expectLogin(login, status().isUnauthorized());
    }

    /**
     * Bloquear login novo não basta: quem já estava logado tem refresh token
     * válido por 7 dias e renovaria o acesso sozinho.
     */
    @Test
    void deactivatingStudentRevokesExistingRefreshToken() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Refresh"));
        long studentId = createStudent(unique("Aluno Com Sessao"), teacherId);
        String login = uniqueLogin("aluno-sessao");
        createStudentUser(login, studentId);

        Cookie refreshCookie = extractCookie(login(login, PASSWORD), REFRESH_COOKIE);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingStudentDoesNotRestoreLoginByItself() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Volta"));
        long studentId = createStudent(unique("Aluno Que Volta"), teacherId);
        String login = uniqueLogin("aluno-volta");
        createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin))
                .andExpect(status().isOk());

        expectLogin(login, status().isUnauthorized());
    }

    @Test
    void adminRestoresAccessExplicitly() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Reconcessao"));
        long studentId = createStudent(unique("Aluno Reconcedido"), teacherId);
        String login = uniqueLogin("aluno-reconcedido");
        long userId = createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);
        expectLogin(login, status().isUnauthorized());

        mockMvc.perform(patch("/users/{id}/activate", userId).cookie(admin))
                .andExpect(status().isOk());

        expectLogin(login, status().isOk());
    }

    /**
     * Entrega é at-least-once, então o listener roda mais de uma vez em
     * cenários de retentativa e precisa ser idempotente.
     */
    @Test
    void revocationIsIdempotent() throws Exception {
        long teacherId = createTeacher(unique("Professor Idempotente"));
        long studentId = createStudent(unique("Aluno Idempotente"), teacherId);
        String login = uniqueLogin("aluno-idempotente");
        createStudentUser(login, studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        awaitAccessRevoked(login);

        expectLogin(login, status().isUnauthorized());
    }

    /**
     * Aluno sem usuário de acesso é o caso comum no começo do estúdio: o
     * listener não pode explodir por não achar ninguém para revogar.
     */
    @Test
    void deactivatingStudentWithoutUserIsHarmless() throws Exception {
        long teacherId = createTeacher(unique("Professor Sem Login"));
        long studentId = createStudent(unique("Aluno Sem Login"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
    }

    /**
     * O registry é o que torna o at-least-once verdadeiro: se o listener
     * tivesse falhado, a linha ficaria sem completion_date e o evento seria
     * reprocessável em vez de sumir.
     */
    @Test
    void eventPublicationIsRecordedAndCompleted() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Registry"));
        long studentId = createStudent(unique("Aluno Do Registry"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
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
     * A revogação chega por evento assíncrono, então a resposta do
     * PATCH /deactivate volta antes de o listener do auth ter rodado. Esperar
     * pelo estado no banco — e não tentar login em loop — evita esbarrar no
     * rate limit de 5 tentativas por minuto e transformar 401 em 429.
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
