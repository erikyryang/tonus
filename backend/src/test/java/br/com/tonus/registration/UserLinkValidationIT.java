package br.com.tonus.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /users cria o usuário de acesso de um aluno/professor já cadastrado.
 * Hoje ele aceita qualquer coisa: id que não existe, role sem id, role com o id
 * do tipo errado. Cada uma dessas combinações produz um login que aponta para
 * lugar nenhum, e nada no sistema reclama depois.
 *
 * Mora no pacote de registration porque precisa dos helpers de cadastro — o
 * comportamento verificado é do auth.
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
     * Role e id têm que concordar: um usuário STUDENT carregando teacher_id
     * seria resolvido como professor por qualquer consumidor do vínculo.
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
     * Criar login para quem já saiu do estúdio produz um acesso que nasce
     * devendo ser revogado: a desativação já passou, então o evento que revoga
     * não vai acontecer de novo. O usuário ficaria ativo indefinidamente.
     */
    @Test
    void rejectsUserForInactiveStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Inativo"));
        long studentId = createStudent(unique("Aluno Que Ja Saiu"), teacherId);
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        postUser(uniqueLogin("aluno-inativo"), "STUDENT", "\"studentId\":" + studentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void rejectsUserForInactiveTeacher() throws Exception {
        long teacherId = createTeacher(unique("Professor Que Ja Saiu"));
        mockMvc.perform(patch("/teachers/{id}/deactivate", teacherId).cookie(admin))
                .andExpect(status().isOk());

        postUser(uniqueLogin("professor-inativo"), "TEACHER", "\"teacherId\":" + teacherId)
                .andExpect(status().isBadRequest());
    }

    /**
     * Quem voltou ao estúdio pode ter login de novo — a recusa é sobre a
     * situação atual, não sobre já ter sido desativado um dia.
     */
    @Test
    void acceptsUserForReactivatedStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Volta"));
        long studentId = createStudent(unique("Aluno Que Voltou"), teacherId);
        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/students/{id}/activate", studentId).cookie(admin))
                .andExpect(status().isOk());

        postUser(uniqueLogin("aluno-voltou"), "STUDENT", "\"studentId\":" + studentId)
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions postUser(
            String login, String role, String linkField) throws Exception {
        String link = linkField == null ? "" : "," + linkField;
        return mockMvc.perform(post("/users")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"%s","password":"%s","role":"%s"%s}
                        """.formatted(login, PASSWORD, role, link)));
    }
}
