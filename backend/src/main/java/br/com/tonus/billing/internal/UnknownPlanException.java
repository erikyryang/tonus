package br.com.tonus.billing.internal;

/**
 * Plan given by the client that doesn't exist. It's 400, not 404: what was
 * wrong was the request body, not the path.
 */
public class UnknownPlanException extends RuntimeException {

    public UnknownPlanException(Long planId) {
        super("Plano desconhecido: " + planId);
    }
}
