package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A teacher who left the studio (inactive) does not receive new students, but the students
 * who were already theirs remain editable — deactivating the teacher must not
 * freeze the registration of whoever remained attached to them.
 */
class InactiveTeacherRuleIT extends AbstractRegistrationIntegrationTest {

    @Test
    void rejectsNewStudentForInactiveTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Saiu"));
        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Orfao","phone":"+5511977770000","teacherId":%d}
                                """.formatted(teacherId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Professor inativo: " + teacherId));
    }

    @Test
    void rejectsMovingStudentToInactiveTeacher() throws Exception {
        long activeTeacher = createTeacher(unique("Professora Ativa"));
        long leavingTeacher = createTeacher(unique("Professor De Saida"));
        long studentId = createStudent(unique("Aluno Movido"), activeTeacher);

        mockMvc.perform(patch("/teachers/{id}/deactivate", leavingTeacher).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/students/{id}", studentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Movido","phone":"+5511977770000","teacherId":%d}
                                """.formatted(leavingTeacher)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stillAllowsEditingStudentOfInactiveTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Sai Depois"));
        long studentId = createStudent(unique("Aluno Que Fica"), teacherId);

        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/students/{id}", studentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Que Fica Renomeado","phone":"+5511977771111","teacherId":%d}
                                """.formatted(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aluno Que Fica Renomeado"));
    }

    /**
     * Order matters: a nonexistent student is 404 for the requested resource, not 400 for the
     * submitted body, even when the body is also invalid.
     */
    @Test
    void unknownStudentWinsOverInvalidTeacher() throws Exception {
        mockMvc.perform(put("/students/{id}", 99_999_999L)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Fantasma","phone":"+5511977770000","teacherId":88888888}
                                """))
                .andExpect(status().isNotFound());
    }
}
