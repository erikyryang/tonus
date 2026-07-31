package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta usuário-por-vínculo (SPEC-009).
 *
 * A tela de alunos escolhe entre "Criar acesso" e "Reativar acesso" linha a
 * linha, e a diferença entre as duas é exatamente o que só o auth sabe: se já
 * existe usuário para aquele aluno e se ele continua ativo. Sem esta consulta o
 * front não teria nem como reativar — a reativação é por id de usuário, e o id
 * nunca chegava até ele.
 *
 * A consulta é em lote, por uma lista de alunos: uma chamada por linha da
 * listagem seriam vinte requisições para montar vinte botões.
 */
class AuthUserLookupIT extends AbstractAuthIntegrationTest {

    private static final String PASSWORD = "s3nha-de-lookup";

    private Cookie admin;

    @BeforeEach
    void authenticateAsAdmin() throws Exception {
        admin = adminCookie("admin-lookup@tonus.com");
    }

    @Test
    void findsTheUsersLinkedToTheGivenStudents() throws Exception {
        long withAccess = createStudent();
        long withoutAccess = createStudent();
        long userId = createStudentUser(withAccess);

        JsonNode users = lookup(withAccess, withoutAccess);

        assertThat(users).hasSize(1);
        JsonNode user = users.get(0);
        assertThat(user.get("id").asLong()).isEqualTo(userId);
        assertThat(user.get("studentId").asLong()).isEqualTo(withAccess);
        assertThat(user.get("active").asBoolean()).isTrue();
        // Nunca o hash de senha, nem por acidente de serialização.
        assertThat(user.has("password")).isFalse();
    }

    /**
     * Acesso revogado precisa ser distinguível de "nunca teve acesso": é a
     * diferença entre oferecer "Reativar acesso" e "Criar acesso".
     *
     * A revogação é assíncrona (evento do cadastro consumido pelo auth), então
     * a espera é pelo efeito, não pela resposta HTTP da desativação.
     */
    @Test
    void reportsRevokedAccessAsInactive() throws Exception {
        long studentId = createStudent();
        createStudentUser(studentId);

        mockMvc.perform(patch("/students/{id}/deactivate", studentId).cookie(admin))
                .andExpect(status().isOk());

        eventually("acesso do aluno revogado", () -> {
            try {
                return !lookup(studentId).get(0).get("active").asBoolean();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void returnsEmptyWhenNoStudentHasAccess() throws Exception {
        assertThat(lookup(createStudent())).isEmpty();
    }

    @Test
    void nonAdminCannotLookUpUsers() throws Exception {
        Cookie teacher = cookieFor("teacher-lookup@tonus.com", Role.TEACHER);

        mockMvc.perform(get("/users").param("studentIds", "1").cookie(teacher))
                .andExpect(status().isForbidden());
    }

    /** Sem alunos não há pergunta a responder — e uma lista vazia esconderia o engano. */
    @Test
    void requiresTheStudentIdsParameter() throws Exception {
        mockMvc.perform(get("/users").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    /**
     * A resposta não é paginada porque o tamanho da pergunta a limita — e é
     * este teto que faz disso uma promessa, não uma esperança. Sem ele a lista
     * vira uma cláusula IN de um parâmetro por id, e o excesso volta como 500.
     */
    @Test
    void rejectsMoreStudentsThanAPageCouldHold() throws Exception {
        String tooMany = String.join(",", java.util.Collections.nCopies(201, "1"));

        mockMvc.perform(get("/users").param("studentIds", tooMany).cookie(admin))
                .andExpect(status().isBadRequest());
    }

    private JsonNode lookup(long... studentIds) throws Exception {
        String ids = String.join(",", Arrays.stream(studentIds).mapToObj(String::valueOf).toList());
        MvcResult result = mockMvc.perform(get("/users").param("studentIds", ids).cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createStudentUser(long studentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"STUDENT","studentId":%d}
                                """.formatted(uniqueLogin("aluno-lookup"), PASSWORD, studentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        return idOf(result);
    }

    private long createStudent() throws Exception {
        return createStudent(admin);
    }
}
