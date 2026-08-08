package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.InvoiceView;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * {@code paymentDate} comes as null while the invoice hasn't been paid.
 *
 * No {@code subscriptionId}: the invoice became the student's (SPEC-012),
 * and there's no more subscription to point to.
 */
public record InvoiceResponse(Long id, Long studentId, String studentName,
                               YearMonth referenceMonth, BigDecimal amount, LocalDate dueDate,
                               InvoiceStatus status, LocalDate paymentDate) {

    static InvoiceResponse from(InvoiceView view) {
        return new InvoiceResponse(view.invoice().getId(), view.studentId(),
                view.studentName(), view.invoice().getReferenceMonth(), view.invoice().getAmount(),
                view.invoice().getDueDate(), view.invoice().getStatus(), view.paymentDate());
    }
}
