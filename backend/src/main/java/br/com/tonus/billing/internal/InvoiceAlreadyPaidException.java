package br.com.tonus.billing.internal;

public class InvoiceAlreadyPaidException extends RuntimeException {

    public InvoiceAlreadyPaidException(Long invoiceId) {
        super("Fatura já paga: " + invoiceId);
    }
}
