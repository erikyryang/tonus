package br.com.tonus.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma fatura acabou de nascer, vencendo hoje.
 *
 * Consome: ninguém ainda. {@code notification} avisará o aluno.
 * Idempotência: o consumidor precisa tolerar o mesmo aviso duas vezes — a
 * entrega é at-least-once.
 */
public record InvoiceDueSoon(Long invoiceId, Long studentId, LocalDate dueDate, BigDecimal amount) {
}
