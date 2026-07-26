package br.com.tonus.auth;

import br.com.tonus.AbstractIntegrationTest;
import br.com.tonus.auth.internal.domain.Role;
import br.com.tonus.auth.internal.domain.User;
import br.com.tonus.auth.internal.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractAuthIntegrationTest extends AbstractIntegrationTest {

    protected static final String ACCESS_COOKIE = "access_token";
    protected static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

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

    protected Cookie extractCookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        if (cookie == null) {
            throw new IllegalStateException("Cookie ausente na resposta: " + name);
        }
        return cookie;
    }
}
