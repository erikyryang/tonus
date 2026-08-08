package br.com.tonus.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A bad token is treated as the absence of a token, never as a server error.
 *
 * The {@code catch} in JwtService.parse is authentication's front door: if
 * someone swaps it for a throw, the API starts returning 500 for any
 * malformed cookie — and leaking the failure reason to whoever is probing.
 *
 * The status is 401, not 403, because here nobody authenticated: 403 is for
 * someone who is authenticated and lacks permission (see AuthRoleAccessIT).
 * The distinction isn't cosmetic — the front treats 401 as "session
 * expired, try the refresh", and with 403 the session would never be
 * renewed.
 */
class AuthInvalidTokenIT extends AbstractAuthIntegrationTest {

    private static final String PROBE = "/test/probe/admin-only";

    @Test
    void garbageTokenIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "isso-nao-e-um-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyTokenIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenShapedButUnsignedIsRejected() throws Exception {
        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, "aaa.bbb.ccc")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A tampered signature is the case that really matters: the payload
     * remains readable and well-formed, and only the cryptographic check
     * separates a legitimate token from a forged one.
     */
    @Test
    void tamperedSignatureIsRejected() throws Exception {
        Cookie valid = adminCookie("admin-token@tonus.com");
        String tampered = flipLastCharacter(valid.getValue());

        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        Cookie valid = adminCookie("admin-token@tonus.com");
        String[] parts = valid.getValue().split("\\.");
        String tampered = parts[0] + "." + flipLastCharacter(parts[1]) + "." + parts[2];

        mockMvc.perform(get(PROBE).cookie(new Cookie(ACCESS_COOKIE, tampered)))
                .andExpect(status().isUnauthorized());
    }

    private String flipLastCharacter(String value) {
        char last = value.charAt(value.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return value.substring(0, value.length() - 1) + replacement;
    }
}
