package br.com.tonus.billing.internal;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(Long id) {
        super("Assinatura não encontrada: " + id);
    }
}
