package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRefreshIT extends AbstractAuthIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * O refresh token vale 7 dias (SPEC-001). Vencido, não renova nada.
     *
     * A expiração é envelhecida direto no banco em vez de mexer no Clock: o
     * bean é compartilhado por totalidade no contexto, e recuá-lo sete dias afetaria
     * qualquer outro teste que rodasse na mesma JVM.
     */
    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        createUser("expirado@tonus.com", "s3cret-pass", Role.ADMIN);
        Cookie refreshCookie = extractCookie(login("expirado@tonus.com", "s3cret-pass"), REFRESH_COOKIE);

        int aged = jdbcTemplate.update("""
                UPDATE refresh_token SET expires_at = NOW() - INTERVAL '1 day'
                WHERE user_id = (SELECT id FROM users WHERE login = ?)
                """, "expirado@tonus.com");
        assertThat(aged).isPositive();

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAccessAndRefreshTokens() throws Exception {
        createUser("teacher@tonus.com", "s3cret-pass", Role.TEACHER);
        MockHttpServletResponse loginResponse = login("teacher@tonus.com", "s3cret-pass");
        Cookie initialRefresh = extractCookie(loginResponse, REFRESH_COOKIE);
        Cookie initialAccess = extractCookie(loginResponse, ACCESS_COOKIE);

        var refreshResult = mockMvc.perform(post("/auth/refresh").cookie(initialRefresh))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(ACCESS_COOKIE))
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andReturn();

        Cookie rotatedRefresh = extractCookie(refreshResult.getResponse(), REFRESH_COOKIE);
        Cookie rotatedAccess = extractCookie(refreshResult.getResponse(), ACCESS_COOKIE);

        assertThat(rotatedRefresh.getValue()).isNotEqualTo(initialRefresh.getValue());
        assertThat(rotatedAccess.getValue()).isNotEqualTo(initialAccess.getValue());
    }

    @Test
    void reusingAlreadyUsedRefreshTokenRevokesWholeFamily() throws Exception {
        createUser("student@tonus.com", "s3cret-pass", Role.STUDENT);
        MockHttpServletResponse loginResponse = login("student@tonus.com", "s3cret-pass");
        Cookie initialRefresh = extractCookie(loginResponse, REFRESH_COOKIE);

        var firstRefreshResult = mockMvc.perform(post("/auth/refresh").cookie(initialRefresh))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefresh = extractCookie(firstRefreshResult.getResponse(), REFRESH_COOKIE);

        mockMvc.perform(post("/auth/refresh").cookie(initialRefresh))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(rotatedRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }
}
