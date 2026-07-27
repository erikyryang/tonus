package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.InvoiceView;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record InvoiceResponse(Long id, Long subscriptionId, Long studentId, YearMonth referenceMonth,
                               BigDecimal amount, LocalDate dueDate, InvoiceStatus status) {

    static InvoiceResponse from(InvoiceView view) {
        return new InvoiceResponse(view.invoice().getId(), view.invoice().getSubscriptionId(), view.studentId(),
                view.invoice().getReferenceMonth(), view.invoice().getAmount(), view.invoice().getDueDate(),
                view.invoice().getStatus());
    }
}
