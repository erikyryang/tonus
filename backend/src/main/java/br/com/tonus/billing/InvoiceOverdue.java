package br.com.tonus.billing;

import java.time.LocalDate;

/**
 * An OPEN invoice passed the grace period and became OVERDUE.
 *
 * Published only on the transition (once): the job's query only scans
 * invoices still OPEN, so running the job again on the same day does not
 * republish it.
 * Consumers: none yet. {@code notification} will notify the student.
 */
public record InvoiceOverdue(Long invoiceId, Long studentId, LocalDate dueDate) {
}
