package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.User;

/**
 * Never exposes the password hash.
 *
 * {@code active} is included because it's what distinguishes "access
 * revoked" from "never had access" for whoever queries a student's user —
 * without it the screen wouldn't know whether to offer create or reactivate
 * (SPEC-009).
 */
public record UserResponse(Long id, String login, String role, Long studentId, Long teacherId, boolean active) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLogin(), user.getRole().name(),
                user.getStudentId(), user.getTeacherId(), user.isActive());
    }
}
