package br.com.tonus.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An invoice was just born, due today.
 *
 * Consumers: none yet. {@code notification} will notify the student.
 * Idempotency: the consumer must tolerate receiving the same notice twice —
 * delivery is at-least-once.
 */
public record InvoiceDueSoon(Long invoiceId, Long studentId, LocalDate dueDate, BigDecimal amount) {
}
