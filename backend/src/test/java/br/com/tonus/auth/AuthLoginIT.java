package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLoginIT extends AbstractAuthIntegrationTest {

    @Test
    void validLoginSetsHttpOnlySameSiteStrictCookies() throws Exception {
        createUser("admin@tonus.com", "s3cret-pass", Role.ADMIN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin@tonus.com","password":"s3cret-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(ACCESS_COOKIE))
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true))
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true));
    }

    @Test
    void validLoginCookiesUseSameSiteStrict() throws Exception {
        createUser("admin2@tonus.com", "s3cret-pass", Role.ADMIN);

        MockHttpServletResponse response = login("admin2@tonus.com", "s3cret-pass");

        assertThat(response.getHeaders("Set-Cookie"))
                .isNotEmpty()
                .allMatch(header -> header.contains("SameSite=Strict"));
    }

    @Test
    void validLoginNeverExposesTokenInResponseBody() throws Exception {
        createUser("admin3@tonus.com", "s3cret-pass", Role.ADMIN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin3@tonus.com","password":"s3cret-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        createUser("admin4@tonus.com", "correct-pass", Role.ADMIN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin4@tonus.com","password":"wrong-pass"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
