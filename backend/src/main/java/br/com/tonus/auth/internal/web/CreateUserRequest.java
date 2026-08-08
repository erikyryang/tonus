package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 255) String login,
        // Minimum of 8, with no composition rule: it's the floor below which
        // the 5 attempts/minute rate limit (LoginRateLimiter) is the only
        // defense against brute force. The cap mirrors users' VARCHAR(255) column.
        @NotBlank @Size(min = 8, max = 255,
                message = "senha deve ter no mínimo 8 caracteres") String password,
        @NotNull Role role,
        Long studentId,
        Long teacherId) {

    /**
     * Role and link have to agree. Without this a STUDENT user carrying a
     * teacher_id would pass, which any consumer of the link would resolve as
     * a teacher — and a STUDENT with no id at all, which resolves to no
     * person. ADMIN represents neither student nor teacher, so it carries no
     * link.
     */
    @AssertTrue(message = "role e vínculo incompatíveis: STUDENT exige studentId, "
            + "TEACHER exige teacherId, ADMIN não aceita vínculo")
    public boolean isLinkConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return switch (role) {
            case STUDENT -> studentId != null && teacherId == null;
            case TEACHER -> teacherId != null && studentId == null;
            case ADMIN -> studentId == null && teacherId == null;
        };
    }
}
