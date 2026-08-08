package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * An invoice's payment: when it was paid and who registered it.
 *
 * registeredBy holds the ADMIN user's id, with no FK: billing can't depend
 * on auth (it would close a cycle with auth -> registration).
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    protected Payment() {
    }

    public Payment(Long invoiceId, LocalDate paymentDate, Long registeredBy) {
        this.invoiceId = invoiceId;
        this.paymentDate = paymentDate;
        this.registeredBy = registeredBy;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public Long getRegisteredBy() {
        return registeredBy;
    }
}
