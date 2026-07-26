package br.com.tonus.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token que não presta é tratado como ausência de token, nunca como erro do
 * servidor.
 *
 * O {@code catch} do JwtService.parse é a porta da frente da autenticação: se
 * alguém trocá-lo por um throw, a API passa a devolver 500 para qualquer cookie
 * malformado — e vazar o motivo da falha para quem está sondando.
 */
class AuthInvalidTokenIT extends AbstractAuthIntegrationTest {

    private static final String PROBE = "/test/probe/admin-only";

    @Test
    void garbageTokenIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "isso-nao-e-um-jwt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyTokenIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenShapedButUnsignedIsRejected() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "aaa.bbb.ccc")))
                .andExpect(status().isForbidden());
    }

    /**
     * Assinatura adulterada é o caso que interessa de verdade: o payload
     * continua legível e bem-formado, e só a verificação criptográfica separa
     * um token legítimo de um forjado.
     */
    @Test
    void tamperedSignatureIsRejected() throws Exception {
        Cookie valid = adminCookie("admin-token@tonus.com");
        String tampered = flipLastCharacter(valid.getValue());

        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, tampered)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        Cookie valid = adminCookie("admin-token@tonus.com");
        String[] parts = valid.getValue().split("\\.");
        String tampered = parts[0] + "." + flipLastCharacter(parts[1]) + "." + parts[2];

        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, tampered)))
                .andExpect(status().isForbidden());
    }

    private String flipLastCharacter(String value) {
        char last = value.charAt(value.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return value.substring(0, value.length() - 1) + replacement;
    }
}
