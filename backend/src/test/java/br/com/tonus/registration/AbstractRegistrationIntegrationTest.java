package br.com.tonus.registration;

import br.com.tonus.auth.AbstractAuthIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for the registration module's tests.
 *
 * The seeding is done through the REST API itself (and not through a repository) on purpose:
 * the repositories live in {@code registration.internal.domain} and the test runs
 * in the module's root package. Using HTTP keeps the test bound to the public contract,
 * which is exactly what the acceptance criteria describe.
 *
 * The tests share a single Postgres for the JVM, with no rollback between
 * classes: every generated name carries a random suffix and no assertion depends
 * on a global count of the table.
 *
 * It is public because scheduling (SPEC-003) needs to seed student and teacher to
 * build the schedule — same reason {@link AbstractAuthIntegrationTest} is
 * public. The coupling with the registration API is confined here.
 */
public abstract class AbstractRegistrationIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    protected ObjectMapper objectMapper;

    protected Cookie admin;

    @BeforeEach
    protected void authenticateAsAdmin() throws Exception {
        admin = adminCookie("admin-registration@tonus.com");
    }

    protected String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected long createTeacher(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/teachers")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"+5511999990000"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long createStudent(String name, long teacherId) throws Exception {
        return createStudent(name, teacherId, "+5511988880000");
    }

    /** Own phone when the test needs to distinguish one student's from another's. */
    protected long createStudent(String name, long teacherId, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phone":"%s","teacherId":%d}
                                """.formatted(name, phone, teacherId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    protected long idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
