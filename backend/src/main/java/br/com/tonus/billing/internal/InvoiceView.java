package br.com.tonus.billing.internal;

import br.com.tonus.billing.internal.domain.Invoice;
import java.time.LocalDate;

/**
 * Invoice paired with what the web needs and the invoice row doesn't hold.
 *
 * studentId and studentName because whoever knows about the student is
 * subscription + registration; paymentDate because the payment lives in the
 * payment table, which has no endpoint of its own. Resolving the three on
 * the client would cost one call per row of the listing (SPEC-008).
 *
 * {@code paymentDate} is null while the invoice hasn't been paid.
 */
public record InvoiceView(Invoice invoice, Long studentId, String studentName, LocalDate paymentDate) {
}
