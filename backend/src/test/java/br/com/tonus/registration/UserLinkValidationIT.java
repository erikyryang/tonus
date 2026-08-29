package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /users creates the access user of an already registered student/teacher.
 * Today it accepts anything: an id that does not exist, a role without an id, a role with an id
 * of the wrong type. Each of these combinations produces a login that points to
 * nowhere, and nothing in the system complains later.
 *
 * It lives in the registration package because it needs the registration helpers — the
 * behavior verified is auth's.
 */
class UserLinkValidationIT extends AbstractRegistrationIntegrationTest {

    private static final String PASSWORD = "outr4-s3nha";

    @Test
    void createsStudentUserWhenStudentExists() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Vinculo"));
        long studentId = createStudent(unique("Aluno Com Login"), teacherId);

        postUser(uniqueLogin("aluno"), "STUDENT", "\"studentId\":" + studentId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId));
    }

    @Test
    void createsTeacherUserWhenTeacherExists() throws Exception {
        long teacherId = createTeacher(unique("Professor Com Login"));

        postUser(uniqueLogin("professor"), "TEACHER", "\"teacherId\":" + teacherId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    @Test
    void createsAdminWithoutAnyLink() throws Exception {
        postUser(uniqueLogin("admin"), "ADMIN", null)
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsStudentUserPointingToNobody() throws Exception {
        postUser(uniqueLogin("fantasma"), "STUDENT", "\"studentId\":99999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void rejectsTeacherUserPointingToNobody() throws Exception {
        postUser(uniqueLogin("fantasma"), "TEACHER", "\"teacherId\":99999999")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStudentRoleWithoutStudentId() throws Exception {
        postUser(uniqueLogin("sem-id"), "STUDENT", null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTeacherRoleWithoutTeacherId() throws Exception {
        postUser(uniqueLogin("sem-id"), "TEACHER", null)
                .andExpect(status().isBadRequest());
    }

    /**
     * Role and id must agree: a STUDENT user carrying teacher_id
     * would be resolved as a teacher by any consumer of the link.
     */
    @Test
    void rejectsStudentRoleCarryingTeacherId() throws Exception {
        long teacherId = createTeacher(unique("Professor Trocado"));

        postUser(uniqueLogin("trocado"), "STUDENT", "\"teacherId\":" + teacherId)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTeacherRoleCarryingStudentId() throws Exception {
        long teacherId = createTeacher(unique("Professora Trocada"));
        long studentId = createStudent(unique("Aluno Trocado"), teacherId);

        postUser(uniqueLogin("trocado"), "TEACHER", "\"studentId\":" + studentId)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAdminCarryingAnyLink() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Admin"));
        long studentId = createStudent(unique("Aluno Do Admin"), teacherId);

        postUser(uniqueLogin("admin-com-id"), "ADMIN", "\"studentId\":" + studentId)
                .andExpect(status().isBadRequest());
        postUser(uniqueLogin("admin-com-id"), "ADMIN", "\"teacherId\":" + teacherId)
                .andExpect(status().isBadRequest());
    }

    /**
     * Creating a login for whoever already left the studio produces access that is born
     * already needing to be revoked: the deactivation already happened, so the event that revokes
     * will not happen again. The user would remain active indefinitely.
     */
    @Test
    void rejectsUserForInactiveStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Inativo"));
        long studentId = createStudent(unique("Aluno Que Ja Saiu"), teacherId);
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        postUser(uniqueLogin("aluno-inativo"), "STUDENT", "\"studentId\":" + studentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void rejectsUserForInactiveTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Ja Saiu"));
        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        postUser(uniqueLogin("professor-inativo"), "TEACHER", "\"teacherId\":" + teacherId)
                .andExpect(status().isBadRequest());
    }

    /**
     * Whoever came back to the studio can have a login again — the rejection is about the
     * current situation, not about having been deactivated once.
     */
    @Test
    void acceptsUserForReactivatedStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Volta"));
        long studentId = createStudent(unique("Aluno Que Voltou"), teacherId);
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        postUser(uniqueLogin("aluno-voltou"), "STUDENT", "\"studentId\":" + studentId)
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions postUser(
            String login, String role, String linkField) throws Exception {
        String link = linkField == null ? "" : "," + linkField;
        return mockMvc.perform(post("/users")
                .cookie(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"%s","password":"%s","role":"%s"%s}
                        """.formatted(login, PASSWORD, role, link)));
    }
}
