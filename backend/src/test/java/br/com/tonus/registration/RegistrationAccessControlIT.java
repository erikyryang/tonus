package br.com.tonus.registration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-002 restricts the registration CRUD to the ADMIN. Logged-in student and teacher
 * cannot list nor read someone else's registration.
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
     * Anonymous is 401 across the whole API since SPEC-006: SecurityConfig's own
     * entry point replaced Spring Security's default 403, because it is the 401
     * that the frontend uses as a signal of "session expired, try the refresh". 403
     * is left for whoever is authenticated and has no permission.
     */
    @Test
    void anonymousCannotAccessRegistrationCrud() throws Exception {
        mockMvc.perform(get("/students")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/teachers")).andExpect(status().isUnauthorized());
    }
}
