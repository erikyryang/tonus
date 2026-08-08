package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /auth/me is what lets the front know who is logged in: the session
 * lives in an httpOnly cookie and the JS reads no token at all.
 *
 * The response comes from the token — role and link are claims — and the
 * name is the user's own login. No querying registration: the link that
 * counts for authorization is the one the token carries, and that's what
 * the front has to reflect.
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
     * 401, not 403: it's the 401 that the front's interceptor uses as the
     * signal for "session expired, try the refresh". With 403 it would never
     * renew the session.
     */
    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A legitimate token from someone who already left the studio doesn't
     * count as a session.
     *
     * The access token is self-contained and survives revocation until it
     * expires. Without this check, whoever was just deactivated would see
     * the application render in full and only discover the cutoff on the
     * next call.
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
