package br.com.tonus.billing.internal;

import br.com.tonus.billing.internal.domain.Invoice;
import java.time.LocalDate;

/**
 * Fatura pareada com o que a web precisa e a linha de invoice não guarda.
 *
 * studentId e studentName porque quem sabe do aluno é subscription +
 * registration; paymentDate porque a baixa mora na tabela payment, que não tem
 * endpoint próprio. Resolver os três no cliente custaria uma chamada por linha
 * da listagem (SPEC-008).
 *
 * {@code paymentDate} é nulo enquanto a fatura não foi paga.
 */
public record InvoiceView(Invoice invoice, Long studentId, String studentName, LocalDate paymentDate) {
}
