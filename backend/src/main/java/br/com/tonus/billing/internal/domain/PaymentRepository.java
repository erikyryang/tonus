package br.com.tonus.billing.internal.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * As baixas de uma página inteira de faturas numa consulta só.
     * uk_payment_invoice garante no máximo uma por fatura.
     */
    List<Payment> findByInvoiceIdIn(Collection<Long> invoiceIds);
}
