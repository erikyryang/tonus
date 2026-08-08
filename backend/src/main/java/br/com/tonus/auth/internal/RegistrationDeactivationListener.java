package br.com.tonus.auth.internal;

import br.com.tonus.registration.StudentDeactivated;
import br.com.tonus.registration.TeacherDeactivated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Whoever leaves the studio loses access to the system.
 *
 * Only revokes; never grants. Reactivating a student in registration doesn't
 * restore the login — that requires an explicit ADMIN
 * `PATCH /users/{id}/activate`, because granting access must not be a side
 * effect of another operation.
 *
 * Delivery is at-least-once, so this listener runs more than once for the
 * same deactivation; idempotency lives in AuthService.revokeAccessOf.
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
