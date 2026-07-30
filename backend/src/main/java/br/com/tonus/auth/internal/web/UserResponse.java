package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.User;

/**
 * Nunca expõe o hash de senha.
 *
 * {@code active} entra porque é o que distingue "acesso revogado" de "nunca
 * teve acesso" para quem consulta o usuário de um aluno — sem ele a tela não
 * saberia se oferece criar ou reativar (SPEC-009).
 */
public record UserResponse(Long id, String login, String role, Long studentId, Long teacherId, boolean active) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLogin(), user.getRole().name(),
                user.getStudentId(), user.getTeacherId(), user.isActive());
    }
}
