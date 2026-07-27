package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.domain.Subscription;
import java.math.BigDecimal;

public record SubscriptionResponse(Long id, Long studentId, BigDecimal monthlyAmount, int dueDay, boolean active) {

    static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getStudentId(),
                subscription.getMonthlyAmount(), subscription.getDueDay(), subscription.isActive());
    }
}
