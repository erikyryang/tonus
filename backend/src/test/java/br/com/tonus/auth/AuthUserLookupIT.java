package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import tools.jackson.databind.JsonNode;
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
 * User-by-link query (SPEC-009).
 *
 * The students screen chooses between "Create access" and "Reactivate
 * access" row by row, and the difference between the two is exactly what
 * only auth knows: whether a user already exists for that student and
 * whether it's still active. Without this query the front wouldn't even be
 * able to reactivate — reactivation is by user id, and the id would never
 * reach it.
 *
 * The query is batched, over a list of students: one call per listing row
 * would be twenty requests just to build twenty buttons.
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
        // Never the password hash, not even by serialization accident.
        assertThat(user.has("password")).isFalse();
    }

    /**
     * Revoked access needs to be distinguishable from "never had access":
     * that's the difference between offering "Reactivate access" and
     * "Create access".
     *
     * Revocation is asynchronous (a registration event consumed by auth), so
     * the wait is for the effect, not for the deactivation's HTTP response.
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

    /** Without students there's no question to answer — an empty list would hide the mistake. */
    @Test
    void requiresTheStudentIdsParameter() throws Exception {
        mockMvc.perform(get("/users").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    /**
     * The response isn't paginated because the size of the question limits
     * it — and it's this cap that makes it a promise, not a hope. Without it
     * the list turns into an IN clause with one parameter per id, and the
     * excess comes back as a 500.
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
