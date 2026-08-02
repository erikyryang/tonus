package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.InvoiceView;
import br.com.tonus.billing.internal.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * {@code paymentDate} vem nulo enquanto a fatura não foi paga.
 *
 * Sem {@code subscriptionId}: a fatura passou a ser do aluno (SPEC-012), e não
 * existe mais assinatura para apontar.
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
