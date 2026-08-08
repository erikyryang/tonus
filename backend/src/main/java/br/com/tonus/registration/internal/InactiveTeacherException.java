package br.com.tonus.registration.internal;

/**
 * A teacher who left the studio does not receive new students. Since deactivation is the
 * way to record the departure, linking a student to an inactive teacher is an input
 * error (400), not a missing resource.
 */
public class InactiveTeacherException extends RuntimeException {

    public InactiveTeacherException(Long teacherId) {
        super("Professor inativo: " + teacherId);
    }
}
