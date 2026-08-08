package br.com.tonus.shared;

import br.com.tonus.auth.AbstractAuthIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-001 acceptance criterion: an error never turns into a stacktrace in
 * the response.
 *
 * ProblemDetailsHandlerIT covers the 4xx cases, which Spring converts on
 * its own — the global handler doesn't even participate in that path. What
 * is verified here is the reason it exists: an exception nobody mapped
 * turns into a 500 in Problem Details, without leaking the class, internal
 * message, or stacktrace.
 */
class UnmappedExceptionIT extends AbstractAuthIntegrationTest {

    @Test
    void unmappedExceptionBecomesProblemDetailsWithoutLeakingInternals() throws Exception {
        Cookie admin = adminCookie("admin-boom@tonus.com");

        mockMvc.perform(get("/test/probe/boom").cookie(admin))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Erro interno inesperado"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("segredo interno"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }
}
