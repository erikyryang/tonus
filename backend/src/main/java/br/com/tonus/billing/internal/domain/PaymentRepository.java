package br.com.tonus.billing.internal.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * The payments for a whole page of invoices in a single query.
     * uk_payment_invoice guarantees at most one per invoice.
     */
    List<Payment> findByInvoiceIdIn(Collection<Long> invoiceIds);
}
