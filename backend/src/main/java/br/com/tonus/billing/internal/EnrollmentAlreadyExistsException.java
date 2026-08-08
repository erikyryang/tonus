package br.com.tonus.billing.internal;

/**
 * The same plan twice for the same student. It would make how much to charge
 * ambiguous; charging double is solved with a plan of a different price.
 */
public class EnrollmentAlreadyExistsException extends RuntimeException {

    public EnrollmentAlreadyExistsException(Long studentId, Long planId) {
        super("O aluno %d já está matriculado no plano %d".formatted(studentId, planId));
    }
}
