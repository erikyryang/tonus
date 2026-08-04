package br.com.tonus.auth.internal.web;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 255) String login,
        // Mínimo de 8, sem regra de composição: é o piso abaixo do qual o rate
        // limit de 5 tentativas/minuto (LoginRateLimiter) é a única defesa
        // contra força bruta. O teto espelha a coluna VARCHAR(255) de users.
        @NotBlank @Size(min = 8, max = 255,
                message = "senha deve ter no mínimo 8 caracteres") String password,
        @NotNull Role role,
        Long studentId,
        Long teacherId) {

    /**
     * Role e vínculo têm que concordar. Sem isso passava um usuário STUDENT
     * carregando teacher_id, que qualquer consumidor do vínculo resolveria como
     * professor — e um STUDENT sem id nenhum, que não resolve para pessoa
     * alguma. ADMIN não representa aluno nem professor, então não leva vínculo.
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
