package br.com.tonus.billing.internal;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(Long id) {
        super("Fatura não encontrada: " + id);
    }
}
