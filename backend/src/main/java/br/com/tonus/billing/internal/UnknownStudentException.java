package br.com.tonus.billing.internal;

/**
 * Não há FK de subscription para student — decisão de arquitetura para não
 * acoplar módulos no schema (docs/ddl.md). Quem garante que o aluno existe é
 * esta validação, contra a RegistrationFacade.
 */
public class UnknownStudentException extends RuntimeException {

    public UnknownStudentException(Long studentId) {
        super("Aluno inexistente ou inativo: " + studentId);
    }
}
