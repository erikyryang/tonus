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
 * O {@code Secure} do cookie de sessão sai do transporte da própria requisição,
 * e não de configuração: HTTP não recebe o atributo, HTTPS recebe.
 *
 * A alternativa era uma flag, e ela tem um modo de falha que este projeto já
 * viveu: em desenvolvimento tudo roda em HTTP puro, e o Safari — que não abre a
 * exceção de {@code localhost} que Chrome e Firefox abrem — descarta um cookie
 * {@code Secure} recebido por HTTP. A sessão nunca começa, e quem digitou a
 * senha certa vê "Login ou senha inválidos", porque o que chega na tela é o 401
 * de {@code /auth/me}. Corrigir isso por flag significa uma configuração que
 * cada máquina precisa lembrar de aplicar — e o esquecimento se manifesta como
 * um sintoma que não aponta para a causa.
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
     * O caso de produção: o Caddy termina o TLS e conversa com a aplicação em
     * HTTP interno, então {@code request.isSecure()} só conta a verdade se o
     * {@code X-Forwarded-Proto} for respeitado.
     *
     * É a asserção que sustenta a decisão inteira — sem ela, derivar do request
     * seria trocar um cookie descartado em desenvolvimento por um cookie sem
     * {@code Secure} em produção, que é o erro grave da dupla. Depende de
     * {@code server.forward-headers-strategy} estar ligado; este teste é o que
     * impede alguém de removê-lo achando que não faz nada.
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
     * O logout apaga o cookie criando outro, vazio — se esse não repetir os
     * atributos do original, o navegador trata como cookie diferente e o
     * antigo continua de pé até expirar.
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
     * Desligar o Secure em HTTP não pode arrastar o resto junto: o que muda em
     * desenvolvimento é o transporte, não a exposição do cookie ao JavaScript
     * da página nem a requisição de terceiro.
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
