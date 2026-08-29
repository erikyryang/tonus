package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.auth.internal.domain.UserRepository;
import br.com.tonus.support.PostgresContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status a missing CSRF token really produces over a servlet container.
 *
 * Same reason {@code AuthorizationStatusIT} exists: MockMvc does not perform
 * the container's ERROR dispatch, so a handler that calls
 * {@code response.sendError(403)} would pass every MockMvc assertion and
 * answer 401 to a real client. The missing-token denial goes through
 * {@code ProblemAccessDeniedHandler} (wired into the CSRF filter), which
 * writes straight to the response — here is where the 403 is actually seen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CsrfStatusIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminCookies;

    @BeforeEach
    void createAdminAndLogIn() throws Exception {
        adminCookies = loginCookies(createUser(Role.ADMIN));
    }

    /** A real client that forgot the CSRF token gets 403 Problem Details, not 401. */
    @Test
    void missingCsrfTokenIs403NotTheRedispatched401() throws Exception {
        HttpResponse<String> response = post("/plans", adminCookies, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
    }

    /** Echoing the XSRF cookie as a header — what the front does — passes. */
    @Test
    void echoingTheCsrfCookieAsHeaderIsAccepted() throws Exception {
        String xsrf = cookieOf(adminCookies, XSRF_COOKIE);
        HttpResponse<String> response = post("/plans", adminCookies, xsrf);

        assertThat(response.statusCode()).isEqualTo(201);
    }

    /** The cookie must exist and be readable by JavaScript (no HttpOnly). */
    @Test
    void loginIssuesACsrfCookieReadableByJavaScript() throws Exception {
        HttpResponse<String> login = CLIENT.send(HttpRequest.newBuilder()
                        .uri(uri("/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"login":"%s","password":"%s"}
                                """.formatted(createUser(Role.ADMIN), PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String xsrfSetCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(XSRF_COOKIE + "="))
                .findFirst()
                .orElseThrow();
        assertThat(xsrfSetCookie).doesNotContain("HttpOnly");
    }

    /** A header that does not match the cookie the request carries is rejected. */
    @Test
    void wrongCsrfHeaderIsRejectedWith403() throws Exception {
        HttpResponse<String> response = post("/plans", adminCookies, "nao-e-o-token");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    /** Refresh mints new access tokens — it is protected, not SameSite-only. */
    @Test
    void refreshWithoutCsrfTokenIs403() throws Exception {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                        .uri(uri("/auth/refresh"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", adminCookies)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    /** The front's refreshSession echoes the cookie too — and the session renews. */
    @Test
    void refreshWithCsrfHeaderIsAccepted() throws Exception {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                        .uri(uri("/auth/refresh"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", adminCookies)
                        .header(XSRF_HEADER, cookieOf(adminCookies, XSRF_COOKIE))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String createUser(Role role) {
        String login = "csrf-status-%s@tonus.com".formatted(UUID.randomUUID().toString().substring(0, 8));
        userRepository.saveAndFlush(
                new User(login, passwordEncoder.encode(PASSWORD), role, null, null));
        return login;
    }

    private String loginCookies(String login) throws Exception {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                        .uri(uri("/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"login":"%s","password":"%s"}
                                """.formatted(login, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream()
                .map(value -> value.substring(0, value.indexOf(';')))
                .reduce((a, b) -> a + "; " + b)
                .orElseThrow();
    }

    private HttpResponse<String> post(String path, String cookies, String xsrfHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"name":"Plano Csrf %d","monthlyPrice":"350.00"}
                        """.formatted(System.nanoTime())));
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        if (xsrfHeader != null) {
            request.header(XSRF_HEADER, xsrfHeader);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookieOf(String cookies, String name) {
        return java.util.Arrays.stream(cookies.split("; "))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }
}