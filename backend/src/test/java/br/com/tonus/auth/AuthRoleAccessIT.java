package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRoleAccessIT extends AbstractAuthIntegrationTest {

    @Test
    void adminAccessesOnlyAdminProbe() throws Exception {
        assertAccess("admin5@tonus.com", Role.ADMIN, "/test/probe/admin-only", true);
        assertAccess("admin5@tonus.com", Role.ADMIN, "/test/probe/teacher-only", false);
        assertAccess("admin5@tonus.com", Role.ADMIN, "/test/probe/student-only", false);
    }

    @Test
    void teacherAccessesOnlyTeacherProbe() throws Exception {
        assertAccess("teacher5@tonus.com", Role.TEACHER, "/test/probe/teacher-only", true);
        assertAccess("teacher5@tonus.com", Role.TEACHER, "/test/probe/admin-only", false);
        assertAccess("teacher5@tonus.com", Role.TEACHER, "/test/probe/student-only", false);
    }

    @Test
    void studentAccessesOnlyStudentProbe() throws Exception {
        assertAccess("student5@tonus.com", Role.STUDENT, "/test/probe/student-only", true);
        assertAccess("student5@tonus.com", Role.STUDENT, "/test/probe/admin-only", false);
        assertAccess("student5@tonus.com", Role.STUDENT, "/test/probe/teacher-only", false);
    }

    private void assertAccess(String login, Role role, String path, boolean allowed) throws Exception {
        if (userRepository.findByLogin(login).isEmpty()) {
            createUser(login, "s3cret-pass", role);
        }
        MockHttpServletResponse loginResponse = login(login, "s3cret-pass");
        Cookie accessCookie = extractCookie(loginResponse, ACCESS_COOKIE);

        var expectedStatus = allowed ? status().isOk() : status().isForbidden();
        mockMvc.perform(get(path).cookie(accessCookie)).andExpect(expectedStatus);
    }
}
