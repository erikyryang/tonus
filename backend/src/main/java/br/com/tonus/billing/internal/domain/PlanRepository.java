package br.com.tonus.billing.internal.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Page<Plan> findByActive(boolean active, Pageable pageable);

    List<Plan> findByActiveTrue();
}
