package br.com.tonus.registration.internal;

/**
 * Student->teacher link pointing to a teacher that does not exist.
 * It is a client error on input (400), not a missing resource (404): the resource
 * requested is the student, and it is the one that is invalid.
 */
public class UnknownTeacherException extends RuntimeException {

    public UnknownTeacherException(Long teacherId) {
        super("Professor inexistente: " + teacherId);
    }
}
