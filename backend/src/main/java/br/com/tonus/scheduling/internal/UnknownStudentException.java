package br.com.tonus.scheduling.internal;

/**
 * Não há FK de default_schedule/schedule_exception para student — decisão de
 * arquitetura para não acoplar módulos no schema (docs/ddl.md). Quem garante
 * que o aluno existe é esta validação, contra a RegistrationFacade.
 */
public class UnknownStudentException extends RuntimeException {

    public UnknownStudentException(Long studentId) {
        super("Aluno inexistente: " + studentId);
    }
}
