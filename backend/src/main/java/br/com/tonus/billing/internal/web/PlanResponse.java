package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.PlanView;
import java.math.BigDecimal;

/**
 * {@code activeEnrollments} is how many students lose the charge if the plan
 * is deactivated. It comes from the backend because the screen needs to say
 * so before confirming, and counting it on the client would require loading
 * the enrollments of the whole studio.
 */
public record PlanResponse(Long id, String name, BigDecimal monthlyPrice, boolean active, long activeEnrollments) {

    public static PlanResponse from(PlanView view) {
        return new PlanResponse(view.plan().getId(), view.plan().getName(), view.plan().getMonthlyPrice(),
                view.plan().isActive(), view.activeEnrollments());
    }
}
