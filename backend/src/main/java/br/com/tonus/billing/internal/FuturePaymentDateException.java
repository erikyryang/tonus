package br.com.tonus.billing.internal;

import java.time.LocalDate;

/**
 * Baixa com data futura. Aceitá-la mandaria ao aluno o recibo de um pagamento
 * que ainda não aconteceu e jogaria o vencimento da assinatura para frente —
 * duas coisas que não têm desfazer.
 */
public class FuturePaymentDateException extends RuntimeException {

    public FuturePaymentDateException(LocalDate paymentDate) {
        super("Data de pagamento no futuro: " + paymentDate);
    }
}
