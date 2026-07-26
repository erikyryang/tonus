package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.User;

/**
 * Nunca expõe o hash de senha.
 */
public record UserResponse(Long id, String login, String role, Long studentId, Long teacherId) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLogin(), user.getRole().name(),
                user.getStudentId(), user.getTeacherId());
    }
}
