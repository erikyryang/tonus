package br.com.tonus.shared;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.tonus.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProblemDetailsHandlerIT extends AbstractIntegrationTest {

    @Test
    void validationErrorReturnsProblemDetailsNotStacktrace() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                // "type" ausente é RFC 9457 válido: omitido, o cliente assume o
                // default "about:blank" — o Jackson 3 do Spring 7 não serializa
                // mais o valor default, diferente do Jackson 2.
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void malformedJsonReturnsProblemDetailsNotStacktrace() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
