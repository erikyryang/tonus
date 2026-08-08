package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A student's invoice for a billing period.
 *
 * One per student per billing period, not one per plan: the student with
 * three plans gets a single charge, with the sum (SPEC-012). {@code amount}
 * is a copy of what was charged — changing a plan's price later doesn't
 * rewrite history.
 *
 * Audited by architecture decision (docs/ddl.md): invoice status transitions
 * need to stay traceable.
 */
@Entity
@Table(name = "invoice")
@Audited
public class Invoice extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    protected Invoice() {
    }

    public Invoice(Long studentId, YearMonth referenceMonth, BigDecimal amount, LocalDate dueDate) {
        this.studentId = studentId;
        this.referenceMonth = referenceMonth.atDay(1);
        this.amount = amount;
        this.dueDate = dueDate;
        this.status = InvoiceStatus.OPEN;
    }

    public void markPaid() {
        this.status = InvoiceStatus.PAID;
    }

    public void markOverdue() {
        this.status = InvoiceStatus.OVERDUE;
    }

    public Long getStudentId() {
        return studentId;
    }

    public YearMonth getReferenceMonth() {
        return YearMonth.from(referenceMonth);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }
}
