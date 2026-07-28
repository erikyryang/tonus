package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /auth/me é o que permite ao front saber quem está logado: a sessão vive
 * em cookie httpOnly e o JS não lê token nenhum.
 *
 * A resposta sai do token — role e vínculo são claims — e o nome é o login do
 * próprio usuário. Nada de consulta a registration: o vínculo que vale para
 * autorização é o que o token carrega, e é ele que o front tem de refletir.
 */
class AuthMeIT extends AbstractAuthIntegrationTest {

    @Test
    void adminHasNoStudentOrTeacherLink() throws Exception {
        String login = "admin-me@tonus.com";
        Cookie cookie = adminCookie(login);

        mockMvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.name").value(login))
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.teacherId").doesNotExist());
    }

    @Test
    void studentCarriesStudentIdFromClaims() throws Exception {
        String login = "student-me@tonus.com";
        createLinkedUser(login, Role.STUDENT, 4242L, null);
        Cookie cookie = cookieFor(login, Role.STUDENT);

        mockMvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.name").value(login))
                .andExpect(jsonPath("$.studentId").value(4242))
                .andExpect(jsonPath("$.teacherId").doesNotExist());
    }

    @Test
    void teacherCarriesTeacherIdFromClaims() throws Exception {
        String login = "teacher-me@tonus.com";
        createLinkedUser(login, Role.TEACHER, null, 777L);
        Cookie cookie = cookieFor(login, Role.TEACHER);

        mockMvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.name").value(login))
                .andExpect(jsonPath("$.teacherId").value(777))
                .andExpect(jsonPath("$.studentId").doesNotExist());
    }

    /**
     * 401, não 403: é o 401 que o interceptor do front usa como sinal de
     * "sessão expirada, tente o refresh". Com 403 ele nunca renovaria a sessão.
     */
    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Token legítimo de quem já saiu do estúdio não vale como sessão.
     *
     * O access token é autocontido e sobrevive à revogação até expirar. Sem
     * esta checagem, quem acabou de ser desligado veria a aplicação montar
     * inteira e só descobriria o corte na chamada seguinte.
     */
    @Test
    void revokedUserNoLongerHasSession() throws Exception {
        String login = "revoked-me@tonus.com";
        createLinkedUser(login, Role.STUDENT, 99L, null);
        Cookie cookie = cookieFor(login, Role.STUDENT);

        mockMvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isOk());

        User user = userRepository.findByLogin(login).orElseThrow();
        user.deactivate();
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void neverLeaksThePasswordHash() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(adminCookie("admin-me@tonus.com")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private void createLinkedUser(String login, Role role, Long studentId, Long teacherId) {
        if (userRepository.findByLogin(login).isPresent()) {
            return;
        }
        userRepository.saveAndFlush(
                new User(login, passwordEncoder.encode(DEFAULT_PASSWORD), role, studentId, teacherId));
    }
}
