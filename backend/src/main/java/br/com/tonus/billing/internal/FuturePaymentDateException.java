package br.com.tonus.billing.internal;

import java.time.LocalDate;

/**
 * Payment with a future date. Accepting it would send the student the
 * receipt for a payment that hasn't happened yet and would push the
 * subscription's due date forward — two things that have no undo.
 */
public class FuturePaymentDateException extends RuntimeException {

    public FuturePaymentDateException(LocalDate paymentDate) {
        super("Data de pagamento no futuro: " + paymentDate);
    }
}
