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
 * The status the API really returns for a role that is not allowed — over a
 * real servlet container.
 *
 * The rest of the suite is MockMvc, and that is exactly why this defect
 * survived: MockMvc does not perform the container's ERROR dispatch, so every
 * test asserting {@code isForbidden()} passed while the running application
 * answered 401. Spring Security's default handler calls
 * {@code response.sendError(403)}, the container re-dispatches to
 * {@code /error}, the security chain filters ERROR dispatches too, the
 * stateless context is gone by then — and the entry point's 401 replaces the
 * 403 that was already decided.
 *
 * The distinction is not cosmetic: by the front end's rule (frontend/CLAUDE.md)
 * a 401 triggers refresh, retry, and then logout. Hitting a route for the wrong
 * role logged the user out instead of telling them they lack permission.
 *
 * Uses the JDK's HttpClient rather than TestRestTemplate, which in Boot 4 lives
 * in a module this project does not have — it uses RestClient, not
 * RestTemplate. A new dependency to make one test talk HTTP is not a trade
 * worth making.
 *
 * Deliberately does NOT extend {@code AbstractIntegrationTest}: that one is
 * {@code WebEnvironment.MOCK}, which is the thing being distrusted here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthorizationStatusIT {

    private static final String PASSWORD = "s3cret-pass";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminCookie;
    private String teacherCookie;

    @BeforeEach
    void createUsersAndLogIn() throws Exception {
        adminCookie = cookieFor(createUser(Role.ADMIN));
        teacherCookie = cookieFor(createUser(Role.TEACHER));
    }

    /**
     * The case that was wrong: authenticated, and without permission for this
     * route. 403 and not 401 — the client already said who it is, and asking it
     * to authenticate again is both untrue and, on this front end, a logout.
     */
    @Test
    void authenticatedWithoutPermissionIs403() throws Exception {
        assertThat(statusOf("/plans", teacherCookie)).isEqualTo(403);
        assertThat(statusOf("/billing-settings", teacherCookie)).isEqualTo(403);
        assertThat(statusOf("/whatsapp/status", teacherCookie)).isEqualTo(403);
    }

    /** The mirror case: an ADMIN on a route reserved for another role. */
    @Test
    void theRuleHoldsInBothDirections() throws Exception {
        assertThat(statusOf("/me/grid", adminCookie)).isEqualTo(403);
    }

    /** And 401 still means 401 — the whole point is that the two stay distinct. */
    @Test
    void withoutASessionIsStill401() throws Exception {
        assertThat(statusOf("/plans", null)).isEqualTo(401);
        assertThat(statusOf("/whatsapp/status", null)).isEqualTo(401);
    }

    /** The permitted role still passes — a blanket 403 would also satisfy the above. */
    @Test
    void theAllowedRoleStillPasses() throws Exception {
        assertThat(statusOf("/plans", adminCookie)).isEqualTo(200);
    }

    /** The denial is Problem Details too, like every other error in the project. */
    @Test
    void theDenialIsProblemDetails() throws Exception {
        HttpResponse<String> response = get("/plans", teacherCookie);

        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        assertThat(response.body()).contains("\"status\":403");
    }

    /** Nothing about the route or the missing role — that is reconnaissance material. */
    @Test
    void theDenialDescribesNothingAboutWhatWasMissing() throws Exception {
        assertThat(get("/plans", teacherCookie).body())
                .doesNotContain("ROLE_")
                .doesNotContain("ADMIN");
    }

    private String createUser(Role role) {
        String login = "authz-%s-%s@tonus.com".formatted(
                role.name().toLowerCase(), UUID.randomUUID().toString().substring(0, 8));
        userRepository.saveAndFlush(
                new User(login, passwordEncoder.encode(PASSWORD), role, null, null));
        return login;
    }

    private String cookieFor(String login) throws Exception {
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
                .filter(value -> value.startsWith("access_token="))
                .map(value -> value.substring(0, value.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }

    private int statusOf(String path, String cookie) throws Exception {
        return get(path, cookie).statusCode();
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(uri(path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }
}
