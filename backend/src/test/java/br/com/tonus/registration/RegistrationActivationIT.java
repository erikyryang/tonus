package br.com.tonus.registration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Student who comes back to the studio. SPEC-002 only describes leaving; coming back is the
 * reverse path of the same field, without creating a new record — the student's
 * invoice history stays linked to the same id.
 */
class RegistrationActivationIT extends AbstractRegistrationIntegrationTest {

    @Test
    void reactivatesStudentKeepingSameId() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Volta"));
        long studentId = createStudent(unique("Aluno Que Volta"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/students/{id}", studentId).cookie(admin))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    @Test
    void reactivationIsIdempotent() throws Exception {
        long teacherId = createTeacher(unique("Professor Idempotente"));
        long studentId = createStudent(unique("Aluno Idempotente"), teacherId);

        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Reactivating a student whose teacher left would bring back a broken link:
     * the admin needs to reassign first.
     */
    @Test
    void rejectsReactivatingStudentOfInactiveTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Sumiu"));
        long studentId = createStudent(unique("Aluno Pendurado"), teacherId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reactivatesTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Retorna"));

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/teachers/{id}/activate", teacherId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void reactivatedTeacherAcceptsStudentsAgain() throws Exception {
        long teacherId = createTeacher(unique("Professora Reintegrada"));

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/teachers/{id}/activate", teacherId).cookie(admin))
                .andExpect(status().isOk());

        createStudent(unique("Aluno Novo Da Reintegrada"), teacherId);
    }

    @Test
    void returnsNotFoundActivatingUnknownStudent() throws Exception {
        mockMvc.perform(patch("/students/{id}/activate", 99_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }
}
