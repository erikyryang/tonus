package br.com.tonus.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment recorded by the ADMIN: invoice became PAID.
 *
 * Consumers: none yet. {@code notification} will notify the student.
 */
public record PaymentRegistered(Long invoiceId, Long studentId, LocalDate paymentDate, BigDecimal amount) {
}
