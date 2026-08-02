package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.domain.PlanEnrollment;

public record PlanEnrollmentResponse(Long id, Long studentId, Long planId, boolean active) {

    public static PlanEnrollmentResponse from(PlanEnrollment enrollment) {
        return new PlanEnrollmentResponse(enrollment.getId(), enrollment.getStudentId(),
                enrollment.getPlanId(), enrollment.isActive());
    }
}
