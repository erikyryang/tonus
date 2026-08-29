package br.com.tonus.billing.internal.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByStudentIdAndReferenceMonth(Long studentId, LocalDate referenceMonth);

    /**
     * PESSIMISTIC_WRITE so the overdue transition can't race a payment:
     * without the lock, the job could read the invoice as OPEN, the ADMIN
     * could pay it (PAID + payment row), and the job's save would then
     * overwrite the status back to OVERDUE — a paid invoice stuck as overdue,
     * with no code path ever reconciling it. The lock serializes the two
     * writers on the row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate cutoff);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    List<Invoice> findByStatus(InvoiceStatus status);

    Page<Invoice> findByStudentId(Long studentId, Pageable pageable);
}
