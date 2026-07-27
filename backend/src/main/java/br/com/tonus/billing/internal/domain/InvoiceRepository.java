package br.com.tonus.billing.internal.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsBySubscriptionIdAndReferenceMonth(Long subscriptionId, LocalDate referenceMonth);

    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate cutoff);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<Invoice> findBySubscriptionId(Long subscriptionId, Pageable pageable);
}
