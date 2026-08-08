package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The coherence between role and link is a pure function: all thirteen
 * combinations can be swept in milliseconds, without bringing up Spring or
 * Postgres. The integration tests cover that a violation turns into a 400
 * with Problem Details; the full table of cases lives here.
 */
class CreateUserRequestTest {

    @ParameterizedTest(name = "{0} com studentId={1} teacherId={2} -> valido={3}")
    @CsvSource(nullValues = "null", value = {
            // STUDENT needs studentId, and only it
            "STUDENT, 1,    null, true",
            "STUDENT, null, null, false",
            "STUDENT, null, 1,    false",
            "STUDENT, 1,    1,    false",

            // TEACHER needs teacherId, and only it
            "TEACHER, null, 1,    true",
            "TEACHER, null, null, false",
            "TEACHER, 1,    null, false",
            "TEACHER, 1,    1,    false",

            // ADMIN represents neither student nor teacher: no link
            "ADMIN,   null, null, true",
            "ADMIN,   1,    null, false",
            "ADMIN,   null, 1,    false",
            "ADMIN,   1,    1,    false",
    })
    void validatesLinkAgainstRole(Role role, Long studentId, Long teacherId, boolean expected) {
        var request = new CreateUserRequest("login@tonus.com", "s3nha", role, studentId, teacherId);

        assertThat(request.isLinkConsistentWithRole()).isEqualTo(expected);
    }

    /**
     * A null role is @NotNull's problem, not this rule's. If this returned
     * false here, the response would carry two violations for the same
     * error — and the second one ("incompatible role and link") would just
     * confuse whoever forgot the field.
     */
    @ParameterizedTest(name = "role nulo com studentId={0} teacherId={1}")
    @CsvSource(nullValues = "null", value = {"null, null", "1, null", "null, 1", "1, 1"})
    void staysSilentWhenRoleIsMissing(Long studentId, Long teacherId) {
        var request = new CreateUserRequest("login@tonus.com", "s3nha", null, studentId, teacherId);

        assertThat(request.isLinkConsistentWithRole()).isTrue();
    }
}
