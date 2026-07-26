package br.com.tonus.registration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aluno que volta ao estúdio. A SPEC-002 só descreve a saída; a volta é o
 * caminho inverso do mesmo campo, sem criar registro novo — o histórico de
 * fatura do aluno continua ligado ao mesmo id.
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
     * Reativar aluno cujo professor saiu traria de volta um vínculo quebrado:
     * o admin precisa remanejar antes.
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
