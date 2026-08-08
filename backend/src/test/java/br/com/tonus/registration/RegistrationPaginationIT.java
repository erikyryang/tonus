package br.com.tonus.registration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion: "Paginated listings from day 1".
 *
 * The assertions are about the shape of the page and the requested size, never about the
 * total count of the table — the Postgres is shared between the test
 * classes and other ITs also insert rows.
 */
class RegistrationPaginationIT extends AbstractRegistrationIntegrationTest {

    @Test
    void paginatesTeachers() throws Exception {
        for (int i = 0; i < 3; i++) {
            createTeacher(unique("Professor Paginado " + i));
        }

        mockMvc.perform(get("/teachers").cookie(admin).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)));
    }

    @Test
    void paginatesStudents() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Turma"));
        for (int i = 0; i < 3; i++) {
            createStudent(unique("Aluno Paginado " + i), teacherId);
        }

        mockMvc.perform(get("/students").cookie(admin).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
    }

    @Test
    void servesSecondPage() throws Exception {
        for (int i = 0; i < 3; i++) {
            createTeacher(unique("Professor Segunda Pagina " + i));
        }

        mockMvc.perform(get("/teachers").cookie(admin).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1));
    }

    /**
     * Without a pagination parameter the response is still a page — the
     * listing never returns a raw array, which is what the project's rule forbids.
     */
    @Test
    void listingIsAlwaysPagedEvenWithoutParams() throws Exception {
        createTeacher(unique("Professor Sem Params"));

        mockMvc.perform(get("/teachers").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.size").exists());
    }
}
