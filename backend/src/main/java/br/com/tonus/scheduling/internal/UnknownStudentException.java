package br.com.tonus.scheduling.internal;

/**
 * There is no FK from default_schedule/schedule_exception to student — an architectural
 * decision to avoid coupling modules in the schema (docs/ddl.md). What guarantees
 * that the student exists is this validation, against the RegistrationFacade.
 */
public class UnknownStudentException extends RuntimeException {

    public UnknownStudentException(Long studentId) {
        super("Aluno inexistente: " + studentId);
    }
}
