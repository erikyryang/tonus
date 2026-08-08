package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLogoutIT extends AbstractAuthIntegrationTest {

    @Test
    void logoutClearsCookies() throws Exception {
        createUser("logout@tonus.com", "s3cret-pass", Role.ADMIN);
        MockHttpServletResponse loginResponse = login("logout@tonus.com", "s3cret-pass");
        Cookie accessCookie = extractCookie(loginResponse, ACCESS_COOKIE);
        Cookie refreshCookie = extractCookie(loginResponse, REFRESH_COOKIE);

        mockMvc.perform(post("/auth/logout").cookie(accessCookie, refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
    }

    @Test
    void refreshTokenIsInvalidatedAfterLogout() throws Exception {
        createUser("logout2@tonus.com", "s3cret-pass", Role.ADMIN);
        MockHttpServletResponse loginResponse = login("logout2@tonus.com", "s3cret-pass");
        Cookie accessCookie = extractCookie(loginResponse, ACCESS_COOKIE);
        Cookie refreshCookie = extractCookie(loginResponse, REFRESH_COOKIE);

        mockMvc.perform(post("/auth/logout").cookie(accessCookie, refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Logout without any cookie is the case of someone who was already
     * logged out, or who cleared their cookies before clicking sign out. It
     * has to be a silent no-op: turning this into an error would just
     * produce noise for an action that already reached the desired outcome.
     */
    @Test
    void logoutWithoutCookiesIsHarmless() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
    }
}
