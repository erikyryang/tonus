package br.com.tonus.billing.internal;

public class EnrollmentNotFoundException extends RuntimeException {

    public EnrollmentNotFoundException(Long id) {
        super("Matrícula não encontrada: " + id);
    }
}
