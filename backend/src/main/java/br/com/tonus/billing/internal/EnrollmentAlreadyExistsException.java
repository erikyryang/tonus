package br.com.tonus.billing.internal;

/**
 * O mesmo plano duas vezes para o mesmo aluno. Tornaria ambíguo quanto cobrar;
 * cobrar em dobro se resolve com um plano de outro preço.
 */
public class EnrollmentAlreadyExistsException extends RuntimeException {

    public EnrollmentAlreadyExistsException(Long studentId, Long planId) {
        super("O aluno %d já está matriculado no plano %d".formatted(studentId, planId));
    }
}
