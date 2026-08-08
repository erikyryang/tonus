package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The session cookie's {@code Secure} comes from the request's own
 * transport, not from configuration: HTTP doesn't get the attribute, HTTPS
 * does.
 *
 * The alternative was a flag, and it has a failure mode this project has
 * already lived through: in development everything runs on plain HTTP, and
 * Safari — which doesn't make the {@code localhost} exception that Chrome
 * and Firefox make — discards a {@code Secure} cookie received over HTTP.
 * The session never starts, and whoever typed the right password sees
 * "Invalid login or password", because what reaches the screen is
 * {@code /auth/me}'s 401. Fixing this with a flag means a setting that every
 * machine has to remember to apply — and forgetting it shows up as a
 * symptom that doesn't point to the cause.
 */
class AuthCookieSecureIT extends AbstractAuthIntegrationTest {

    @Test
    void cookiesOverPlainHttpComeWithoutSecure() throws Exception {
        String login = uniqueLogin("cookie-http");
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        MockHttpServletResponse response = login(login, DEFAULT_PASSWORD);

        assertThat(response.getHeaders("Set-Cookie"))
                .isNotEmpty()
                .noneMatch(header -> header.contains("; Secure"));
    }

    @Test
    void cookiesOverHttpsComeWithSecure() throws Exception {
        String login = uniqueLogin("cookie-https");
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .isNotEmpty()
                .allMatch(header -> header.contains("; Secure"));
    }

    /**
     * The production case: Caddy terminates TLS and talks to the application
     * over internal HTTP, so {@code request.isSecure()} only tells the truth
     * if {@code X-Forwarded-Proto} is honored.
     *
     * This is the assertion that backs the whole decision — without it,
     * deriving from the request would trade a cookie discarded in
     * development for a cookie without {@code Secure} in production, which
     * is the serious error of the pair. It depends on
     * {@code server.forward-headers-strategy} being enabled; this test is
     * what stops someone from removing it thinking it does nothing.
     */
    @Test
    void forwardedProtoFromTheReverseProxyMakesTheCookieSecure() throws Exception {
        String login = uniqueLogin("cookie-forwarded");
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .isNotEmpty()
                .allMatch(header -> header.contains("; Secure"));
    }

    /**
     * Logout clears the cookie by creating another one, empty — if that one
     * doesn't repeat the original's attributes, the browser treats it as a
     * different cookie and the old one stays standing until it expires.
     */
    @Test
    void logoutClearsCookiesWithTheSameSecureAttribute() throws Exception {
        String login = uniqueLogin("cookie-logout");
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .secure(true)
                        .cookie(extractCookie(login(login, DEFAULT_PASSWORD), REFRESH_COOKIE)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .isNotEmpty()
                .allMatch(header -> header.contains("; Secure"));
    }

    /**
     * Turning off Secure on HTTP can't drag the rest along with it: what
     * changes in development is the transport, not the cookie's exposure to
     * the page's JavaScript nor third-party requests.
     */
    @Test
    void plainHttpCookiesKeepHttpOnlyAndSameSiteStrict() throws Exception {
        String login = uniqueLogin("cookie-http-rest");
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        MockHttpServletResponse response = login(login, DEFAULT_PASSWORD);

        assertThat(response.getHeaders("Set-Cookie"))
                .isNotEmpty()
                .allMatch(header -> header.contains("HttpOnly"))
                .allMatch(header -> header.contains("SameSite=Strict"));
    }

    private String credentials(String login) {
        return """
                {"login":"%s","password":"%s"}
                """.formatted(login, DEFAULT_PASSWORD);
    }
}
