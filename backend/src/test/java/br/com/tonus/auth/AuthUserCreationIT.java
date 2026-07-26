package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A SPEC-002 pede que o ADMIN crie o usuário de acesso do aluno/professor.
 * O endpoint mora no auth porque é o auth que é dono da tabela users; o vínculo
 * é validado contra o cadastro.
 *
 * Aluno e professor são criados de verdade em cada teste: o vínculo precisa
 * apontar para alguém que existe e está ativo, então id chutado não serve.
 */
class AuthUserCreationIT extends AbstractAuthIntegrationTest {

    private static final String PASSWORD = "outr4-s3nha";

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie admin;

    @BeforeEach
    void authenticateAsAdmin() throws Exception {
        admin = adminCookie("admin-users@tonus.com");
    }

    @Test
    void adminCreatesStudentUserAndTheUserCanLogIn() throws Exception {
        long studentId = createStudent();
        String login = uniqueLogin("aluno-novo");

        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"STUDENT","studentId":%d}
                                """.formatted(login, PASSWORD, studentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.password").doesNotExist());

        login(login, PASSWORD);
    }

    @Test
    void adminCreatesTeacherUser() throws Exception {
        long teacherId = createTeacher();
        String login = uniqueLogin("professor-novo");

        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"TEACHER","teacherId":%d}
                                """.formatted(login, PASSWORD, teacherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    @Test
    void rejectsDuplicateLogin() throws Exception {
        long studentId = createStudent();
        String login = uniqueLogin("duplicado");
        String body = """
                {"login":"%s","password":"%s","role":"STUDENT","studentId":%d}
                """.formatted(login, PASSWORD, studentId);

        mockMvc.perform(post("/users").cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/users").cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUserWithoutLogin() throws Exception {
        long studentId = createStudent();

        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s","role":"STUDENT","studentId":%d}
                                """.formatted(PASSWORD, studentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotCreateUsers() throws Exception {
        Cookie teacher = cookieFor("teacher-users@tonus.com", Role.TEACHER);

        mockMvc.perform(post("/users")
                        .cookie(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"x@tonus.com","password":"%s","role":"STUDENT","studentId":1}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundActivatingUnknownUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/users/{id}/activate", 99_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    private long createTeacher() throws Exception {
        MvcResult result = mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Professor Do Auth %d","phone":"+5511999990000"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createStudent() throws Exception {
        long teacherId = createTeacher();
        MvcResult result = mockMvc.perform(post("/students")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aluno Do Auth %d","phone":"+5511988880000","teacherId":%d}
                                """.formatted(System.nanoTime(), teacherId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String uniqueLogin(String prefix) {
        return prefix + "-" + System.nanoTime() + "@tonus.com";
    }
}
