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
     * Logout sem cookie nenhum é o caso de quem já estava deslogado, ou de quem
     * limpou os cookies antes de clicar em sair. Tem que ser no-op silencioso:
     * transformar isso em erro só produziria ruído numa ação que já alcançou o
     * resultado desejado.
     */
    @Test
    void logoutWithoutCookiesIsHarmless() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
    }
}
