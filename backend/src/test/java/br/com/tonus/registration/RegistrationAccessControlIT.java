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
     * O entry point atual do Spring Security devolve 403 para anônimo, não 401.
     * Se 401 é o certo, a mudança é no SecurityConfig do auth (SPEC-001) e vale
     * para toda a API — não é decisão desta spec. Aqui só se garante que o
     * anônimo não passa.
     */
    @Test
    void anonymousCannotAccessRegistrationCrud() throws Exception {
        mockMvc.perform(get("/students")).andExpect(status().isForbidden());
        mockMvc.perform(get("/teachers")).andExpect(status().isForbidden());
    }
}
