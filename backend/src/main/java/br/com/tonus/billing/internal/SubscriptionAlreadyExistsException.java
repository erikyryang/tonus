package br.com.tonus.billing.internal;

/**
 * Um aluno tem no máximo uma assinatura: é o critério de aceite de
 * uk_subscription_student expresso na camada de serviço.
 */
public class SubscriptionAlreadyExistsException extends RuntimeException {

    public SubscriptionAlreadyExistsException(Long studentId) {
        super("Aluno já tem assinatura: " + studentId);
    }
}
