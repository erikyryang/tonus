package br.com.tonus.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Baixa registrada pelo ADMIN: fatura virou PAGA.
 *
 * Consome: ninguém ainda. {@code notification} avisará o aluno.
 */
public record PaymentRegistered(Long invoiceId, Long studentId, LocalDate paymentDate, BigDecimal amount) {
}
