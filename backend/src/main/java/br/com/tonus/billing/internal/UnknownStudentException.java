package br.com.tonus.billing.internal;

/**
 * There is no FK from subscription to student — an architecture decision to
 * avoid coupling modules in the schema (docs/ddl.md). What guarantees the
 * student exists is this validation, against the RegistrationFacade.
 */
public class UnknownStudentException extends RuntimeException {

    public UnknownStudentException(Long studentId) {
        super("Aluno inexistente ou inativo: " + studentId);
    }
}
