package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion: "Deactivating a student does not affect existing invoices".
 *
 * The billing module does not exist yet, so what is tested here is the invariant
 * that this criterion depends on: deactivating is an UPDATE (active=false), the row
 * survives with the link intact and there is no physical DELETE to erase the
 * history. The test with a real invoice belongs to the billing spec.
 */
class RegistrationDeactivationIT extends AbstractRegistrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deactivatesStudentKeepingRowAndTeacherLink() throws Exception {
        long teacherId = createTeacher(unique("Professora Luiza"));
        long studentId = createStudent(unique("Aluno Marcos"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/students/{id}", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.teacherId").value(teacherId));

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student WHERE id = ? AND teacher_id = ? AND active = FALSE",
                Long.class, studentId, teacherId);
        assertThat(rows).isEqualTo(1L);
    }

    @Test
    void deactivationIsIdempotent() throws Exception {
        long teacherId = createTeacher(unique("Professor Nelson"));
        long studentId = createStudent(unique("Aluna Olivia"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatesTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Paulo"));

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/teachers/{id}", teacherId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void studentHasNoPhysicalDelete() throws Exception {
        long teacherId = createTeacher(unique("Professora Rita"));
        long studentId = createStudent(unique("Aluno Sergio"), teacherId);

        mockMvc.perform(delete("/students/{id}", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/students/{id}", studentId).cookie(admin))
                .andExpect(status().isOk());
    }

    @Test
    void teacherHasNoPhysicalDelete() throws Exception {
        long teacherId = createTeacher(unique("Professor Tulio"));

        mockMvc.perform(delete("/teachers/{id}", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
