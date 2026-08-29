package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shape of CSRF enforcement on MockMvc: what is rejected, what passes,
 * what is exempt.
 *
 * The real cookie handshake — login issuing the {@code XSRF-TOKEN} cookie and
 * the front echoing it as a header — lives in {@code CsrfStatusIT}, over a real
 * container. MockMvc cannot be trusted for it: Spring Security's {@code csrf()}
 * post-processor swaps the shared {@code CsrfFilter} repository by reflection,
 * so once any test in the context uses {@code .with(csrf())}, the server stops
 * issuing the real cookie.
 */
class CsrfProtectionIT extends AbstractAuthIntegrationTest {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejectedWith403() throws Exception {
        Cookie admin = adminCookie("csrf-sem-token@tonus.com");

        mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Professor Sem Token","phone":"+5511999900001"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void stateChangingRequestWithWrongCsrfTokenIsRejectedWith403() throws Exception {
        Cookie admin = adminCookie("csrf-token-errado@tonus.com");
        Cookie xsrf = new Cookie(XSRF_COOKIE, "qualquer-coisa");

        mockMvc.perform(post("/teachers")
                        .cookie(admin, xsrf)
                        .header(XSRF_HEADER, "outra-coisa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Professor Token Errado","phone":"+5511999900002"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRequestsDoNotRequireCsrfToken() throws Exception {
        mockMvc.perform(get("/plans").cookie(adminCookie("csrf-get@tonus.com")))
                .andExpect(status().isOk());
    }

    /**
     * Refresh and logout are protected like any other state-changing endpoint:
     * refresh mints new access tokens, so SameSite=Strict on the refresh
     * cookie is not its only wall. Login is the only exemption, because it is
     * the cold start — there is no cookie to echo yet.
     */
    @Test
    void refreshAndLogoutRequireCsrfToken() throws Exception {
        createUser("csrf-lifecycle@tonus.com", DEFAULT_PASSWORD, Role.ADMIN);
        MockHttpServletResponse loginResponse = login("csrf-lifecycle@tonus.com", DEFAULT_PASSWORD);
        Cookie refresh = extractCookie(loginResponse, REFRESH_COOKIE);

        mockMvc.perform(post("/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/logout").cookie(refresh))
                .andExpect(status().isForbidden());
    }
}