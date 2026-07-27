package br.com.tonus.auth;

import br.com.tonus.AbstractIntegrationTest;
import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.auth.internal.domain.UserRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class AbstractAuthIntegrationTest extends AbstractIntegrationTest {

    protected static final String ACCESS_COOKIE = "access_token";
    protected static final String REFRESH_COOKIE = "refresh_token";
    protected static final String DEFAULT_PASSWORD = "s3cret-pass";

    private static final Map<String, Cookie> COOKIE_CACHE = new ConcurrentHashMap<>();

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
     * Garante um usuário com o role pedido e devolve o cookie de access dele.
     * Existe para que testes de outros módulos obtenham uma sessão autenticada
     * sem importar {@code auth.internal.*} — o acoplamento com os detalhes do
     * auth fica confinado aqui.
     *
     * O cookie é cacheado para a JVM inteira: o POST /auth/login tem rate limit
     * de 5 tentativas/minuto por usuário, e autenticar de novo a cada
     * @BeforeEach estouraria o limite (429) em qualquer classe com mais de
     * cinco testes. O access token vale 10 min, de sobra para a suíte.
     *
     * A chave inclui o {@code Clock} do contexto, e não só o login. Um token
     * nasce e é conferido contra o relógio de quem o emitiu, e as classes de
     * teste não compartilham relógio — billing, scheduling e os testes de
     * relógio real sobem contextos com {@code Clock} próprio. Cacheado só por
     * login, o cookie emitido sob um relógio vazaria para um contexto com
     * outro e chegaria expirado. Identidade do bean é o recorte exato de "o
     * contexto que emitiu este token".
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
}
