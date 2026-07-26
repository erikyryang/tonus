package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite: "Desativar aluno não afeta faturas existentes".
 *
 * O módulo billing ainda não existe, então o que se testa aqui é o invariante
 * do qual esse critério depende: desativar é UPDATE (active=false), a linha
 * sobrevive com o vínculo intacto e não existe DELETE físico para apagar o
 * histórico. O teste com fatura real entra na spec de billing.
 */
class RegistrationDeactivationIT extends AbstractRegistrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deactivatesStudentKeepingRowAndTeacherLink() throws Exception {
        long teacherId = createTeacher(unique("Professora Luiza"));
        long studentId = createStudent(unique("Aluno Marcos"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
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

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatesTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Paulo"));

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
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

        mockMvc.perform(delete("/students/{id}", studentId).cookie(admin))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/students/{id}", studentId).cookie(admin))
                .andExpect(status().isOk());
    }

    @Test
    void teacherHasNoPhysicalDelete() throws Exception {
        long teacherId = createTeacher(unique("Professor Tulio"));

        mockMvc.perform(delete("/teachers/{id}", teacherId).cookie(admin))
                .andExpect(status().isMethodNotAllowed());
    }
}
