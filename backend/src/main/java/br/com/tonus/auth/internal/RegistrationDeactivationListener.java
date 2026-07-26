package br.com.tonus.auth.internal;

import br.com.tonus.registration.StudentDeactivated;
import br.com.tonus.registration.TeacherDeactivated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Quem sai do estúdio perde o acesso ao sistema.
 *
 * Só revoga; não concede. Reativar um aluno no cadastro não devolve o login —
 * isso exige `PATCH /users/{id}/activate` explícito do ADMIN, porque conceder
 * acesso não deve ser efeito colateral de outra operação.
 *
 * A entrega é at-least-once, então este listener roda mais de uma vez para o
 * mesmo desligamento; a idempotência mora em AuthService.revokeAccessOf.
 */
@Component
public class RegistrationDeactivationListener {

    private final AuthService authService;

    RegistrationDeactivationListener(AuthService authService) {
        this.authService = authService;
    }

    @ApplicationModuleListener
    public void on(StudentDeactivated event) {
        authService.revokeAccessOfStudent(event.studentId());
    }

    @ApplicationModuleListener
    public void on(TeacherDeactivated event) {
        authService.revokeAccessOfTeacher(event.teacherId());
    }
}
