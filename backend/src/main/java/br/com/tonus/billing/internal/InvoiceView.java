package br.com.tonus.billing.internal;

import br.com.tonus.billing.internal.domain.Invoice;

/**
 * Fatura pareada com o id do aluno da assinatura dona dela. Existe porque
 * invoice não guarda studentId (quem sabe isso é subscription), e a web
 * precisa das duas coisas juntas para montar a resposta.
 */
public record InvoiceView(Invoice invoice, Long studentId) {
}
