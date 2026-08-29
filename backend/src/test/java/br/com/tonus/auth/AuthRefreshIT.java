package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.RefreshToken;
import br.com.tonus.auth.internal.domain.RefreshTokenRepository;
import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRefreshIT extends AbstractAuthIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /**
     * The refresh token is valid for 7 days (SPEC-001). Expired, it renews
     * nothing.
     *
     * The expiration is aged directly in the database instead of touching
     * the Clock: the bean is shared across the whole context, and setting it
     * back seven days would affect any other test running in the same JVM.
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

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAccessAndRefreshTokens() throws Exception {
        createUser("teacher@tonus.com", "s3cret-pass", Role.TEACHER);
        MockHttpServletResponse loginResponse = login("teacher@tonus.com", "s3cret-pass");
        Cookie initialRefresh = extractCookie(loginResponse, REFRESH_COOKIE);
        Cookie initialAccess = extractCookie(loginResponse, ACCESS_COOKIE);

        var refreshResult = mockMvc.perform(post("/auth/refresh").cookie(initialRefresh).with(csrf()))
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

        var firstRefreshResult = mockMvc.perform(post("/auth/refresh").cookie(initialRefresh).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefresh = extractCookie(firstRefreshResult.getResponse(), REFRESH_COOKIE);

        mockMvc.perform(post("/auth/refresh").cookie(initialRefresh).with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(rotatedRefresh).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A deactivated user must not renew access through an unused refresh
     * token. The deactivation listener revokes tokens asynchronously, and in
     * the window before it runs the token is still valid — the check here is
     * what closes that gap.
     */
    @Test
    void refreshIsRejectedForDeactivatedUser() throws Exception {
        User user = createUser("desativado@tonus.com", "s3cret-pass", Role.ADMIN);
        Cookie refreshCookie = extractCookie(login("desativado@tonus.com", "s3cret-pass"), REFRESH_COOKIE);

        jdbcTemplate.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The reuse detection must survive concurrency: two requests carrying the
     * same token both read {@code used=false}, and only the conditional UPDATE
     * may win. The repository method returning 1 exactly once is what makes
     * the loser be treated as a replay instead of silently issuing a second
     * session in the same family.
     */
    @Test
    @Transactional
    void atomicConsumeWinsOnlyOnce() throws Exception {
        User user = createUser("race@tonus.com", "s3cret-pass", Role.ADMIN);
        login("race@tonus.com", "s3cret-pass");
        RefreshToken token = refreshTokenRepository.findByUserIdAndUsedFalse(user.getId()).stream()
                .findFirst()
                .orElseThrow();

        assertThat(refreshTokenRepository.markUsedIfUnused(token.getId())).isEqualTo(1);
        assertThat(refreshTokenRepository.markUsedIfUnused(token.getId())).isEqualTo(0);
    }
}
