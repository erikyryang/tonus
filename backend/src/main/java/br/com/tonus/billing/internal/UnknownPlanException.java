package br.com.tonus.billing.internal;

/**
 * Plano informado pelo cliente que não existe. É 400, e não 404: quem errou foi
 * o corpo da requisição, não o caminho.
 */
public class UnknownPlanException extends RuntimeException {

    public UnknownPlanException(Long planId) {
        super("Plano desconhecido: " + planId);
    }
}
