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
 * Critério de aceite da SPEC-001: erro nunca vira stacktrace na resposta.
 *
 * O ProblemDetailsHandlerIT cobre os 4xx, que o Spring converte sozinho — o
 * handler global nem participa daquele caminho. O que se verifica aqui é a
 * razão de existir dele: exceção que ninguém mapeou vira 500 em Problem
 * Details, sem vazar classe, mensagem interna nem stacktrace.
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
