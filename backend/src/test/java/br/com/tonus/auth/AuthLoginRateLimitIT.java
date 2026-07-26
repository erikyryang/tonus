package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthLoginRateLimitIT extends AbstractAuthIntegrationTest {

    @Test
    void exceedingLoginAttemptsReturnsTooManyRequests() throws Exception {
        createUser("ratelimit@tonus.com", "correct-pass", Role.ADMIN);

        String wrongPasswordPayload = """
                {"login":"ratelimit@tonus.com","password":"wrong-pass"}
                """;

        boolean sawTooManyRequests = false;
        for (int attempt = 0; attempt < 20 && !sawTooManyRequests; attempt++) {
            var result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordPayload))
                    .andReturn();
            if (result.getResponse().getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                sawTooManyRequests = true;
            }
        }

        assertThat(sawTooManyRequests)
                .as("POST /auth/login deve responder 429 após exceder o limite de tentativas")
                .isTrue();
    }
}
