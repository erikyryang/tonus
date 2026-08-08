package br.com.tonus.billing.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A student's link to a plan.
 *
 * Deactivate instead of delete, as in the rest of the system: "did this
 * student ever have this plan" is a billing question, and a deleted row
 * can't answer it.
 *
 * {@code studentId} has no FK, same decision as {@code
 * subscription.student_id} (see docs/ddl.md); {@code planId} has a real FK,
 * because plan and enrollment belong to the same module.
 */
@Entity
@Table(name = "plan_enrollment")
public class PlanEnrollment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private boolean active;

    protected PlanEnrollment() {
    }

    public PlanEnrollment(Long studentId, Long planId) {
        this.studentId = studentId;
        this.planId = planId;
        this.active = true;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getPlanId() {
        return planId;
    }

    public boolean isActive() {
        return active;
    }
}
