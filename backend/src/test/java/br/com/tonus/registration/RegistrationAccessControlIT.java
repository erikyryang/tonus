package br.com.tonus.registration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A SPEC-002 restringe o CRUD de cadastro ao ADMIN. Aluno e professor logados
 * não podem listar nem ler o cadastro alheio.
 */
class RegistrationAccessControlIT extends AbstractRegistrationIntegrationTest {

    @Test
    void teacherCannotAccessRegistrationCrud() throws Exception {
        Cookie teacher = teacherCookie("teacher-registration@tonus.com");

        mockMvc.perform(get("/students").cookie(teacher)).andExpect(status().isForbidden());
        mockMvc.perform(get("/teachers").cookie(teacher)).andExpect(status().isForbidden());
    }

    @Test
    void studentCannotAccessRegistrationCrud() throws Exception {
        Cookie student = studentCookie("student-registration@tonus.com");

        mockMvc.perform(get("/students").cookie(student)).andExpect(status().isForbidden());
        mockMvc.perform(get("/teachers").cookie(student)).andExpect(status().isForbidden());
    }

    /**
     * Anônimo é 401 em toda a API desde a SPEC-006: o entry point próprio do
     * SecurityConfig substituiu o 403 padrão do Spring Security, porque é o 401
     * que o front usa como sinal de "sessão expirada, tente o refresh". 403
     * ficou para quem está autenticado e não tem permissão.
     */
    @Test
    void anonymousCannotAccessRegistrationCrud() throws Exception {
        mockMvc.perform(get("/students")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/teachers")).andExpect(status().isUnauthorized());
    }
}
