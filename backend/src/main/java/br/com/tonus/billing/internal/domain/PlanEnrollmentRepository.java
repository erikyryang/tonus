package br.com.tonus.billing.internal.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanEnrollmentRepository extends JpaRepository<PlanEnrollment, Long> {

    List<PlanEnrollment> findByStudentId(Long studentId);

    boolean existsByStudentIdAndPlanId(Long studentId, Long planId);

    /** The daily job's input: every live enrollment in the studio, in one query. */
    List<PlanEnrollment> findByActiveTrue();

    long countByPlanIdAndActiveTrue(Long planId);

    /**
     * How many active students each plan has, for the whole page at once.
     *
     * Exists because the listing shows the count on every row: one query per
     * plan is N+1 on a screen the ADMIN opens to decide to deactivate
     * something — exactly where the count matters. A plan with no
     * enrollment doesn't appear in the result, and the caller treats its
     * absence as zero.
     */
    @Query("""
            SELECT e.planId AS planId, COUNT(e) AS total
            FROM PlanEnrollment e
            WHERE e.active = true AND e.planId IN :planIds
            GROUP BY e.planId
            """)
    List<PlanEnrollmentCount> countActiveByPlanIds(Collection<Long> planIds);

    interface PlanEnrollmentCount {

        Long getPlanId();

        long getTotal();
    }
}
