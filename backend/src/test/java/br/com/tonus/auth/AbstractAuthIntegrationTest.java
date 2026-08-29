package br.com.tonus.auth;

import br.com.tonus.AbstractIntegrationTest;
import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.auth.internal.domain.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class AbstractAuthIntegrationTest extends AbstractIntegrationTest {

    protected static final String ACCESS_COOKIE = "access_token";
    protected static final String REFRESH_COOKIE = "refresh_token";
    protected static final String DEFAULT_PASSWORD = "s3cret-pass";

    private static final Map<String, Cookie> COOKIE_CACHE = new ConcurrentHashMap<>();

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected Clock clock;

    protected User createUser(String login, String rawPassword, Role role) {
        User user = new User(login, passwordEncoder.encode(rawPassword), role, null, null);
        return userRepository.saveAndFlush(user);
    }

    protected MockHttpServletResponse login(String login, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s"}
                                """.formatted(login, rawPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse();
    }

    /**
     * Guarantees a user with the requested role and returns their access
     * cookie. Exists so that tests in other modules get an authenticated
     * session without importing {@code auth.internal.*} — coupling to auth's
     * details stays confined here.
     *
     * The cookie is cached for the whole JVM: POST /auth/login has a rate
     * limit of 5 attempts/minute per user, and authenticating again on every
     * @BeforeEach would blow past the limit (429) in any class with more than
     * five tests. The access token is valid for 10 min, plenty for the suite.
     *
     * The key includes the context's {@code Clock}, not just the login. A
     * token is born and checked against the clock of whoever issued it, and
     * test classes don't share a clock — billing, scheduling, and the
     * real-clock tests bring up contexts with their own {@code Clock}. Cached
     * only by login, a cookie issued under one clock would leak into a
     * context with another and arrive expired. Bean identity is the exact
     * cut of "the context that issued this token".
     */
    protected Cookie cookieFor(String login, Role role) throws Exception {
        String key = login + "#" + System.identityHashCode(clock);
        Cookie cached = COOKIE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (userRepository.findByLogin(login).isEmpty()) {
            createUser(login, DEFAULT_PASSWORD, role);
        }
        Cookie cookie = extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
        COOKIE_CACHE.put(key, cookie);
        return cookie;
    }

    protected Cookie adminCookie(String login) throws Exception {
        return cookieFor(login, Role.ADMIN);
    }

    protected Cookie teacherCookie(String login) throws Exception {
        return cookieFor(login, Role.TEACHER);
    }

    protected Cookie studentCookie(String login) throws Exception {
        return cookieFor(login, Role.STUDENT);
    }

    protected Cookie extractCookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        if (cookie == null) {
            throw new IllegalStateException("Cookie ausente na resposta: " + name);
        }
        return cookie;
    }

    /**
     * A real student and teacher, created through the registration API.
     *
     * They live here because auth tests need a link that actually exists:
     * creating a user checks the student against registration. They used to
     * be a copy per test class, and a change to POST /students's contract
     * would require hunting down all of them — the second one left behind
     * would fail for a reason that has nothing to do with what it's testing.
     *
     * The name carries System.nanoTime() because the database is shared by
     * the suite and the phone number has its own uniqueness constraint.
     */
    protected long createTeacher(Cookie admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Professor De Teste %d","phone":"+5511999990000"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long createStudent(Cookie admin) throws Exception {
        return createStudent(admin, createTeacher(admin));
    }

    protected long createStudent(Cookie admin, long teacherId) throws Exception {
        MvcResult result = mockMvc.perform(post("/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno De Teste %d","phone":"+5511988880000","teacherId":%d}
                                """.formatted(System.nanoTime(), teacherId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Unique login per call: the database is shared and login is UNIQUE. */
    protected String uniqueLogin(String prefix) {
        return prefix + "-" + System.nanoTime() + "@tonus.com";
    }
}
