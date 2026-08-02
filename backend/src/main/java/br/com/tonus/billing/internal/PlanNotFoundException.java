package br.com.tonus.billing.internal;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(Long id) {
        super("Plano não encontrado: " + id);
    }
}
