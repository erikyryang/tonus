package br.com.tonus.billing.internal.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByStudentId(Long studentId);

    Optional<Subscription> findByStudentId(Long studentId);

    Page<Subscription> findByActive(boolean active, Pageable pageable);

    List<Subscription> findByActiveTrue();
}
