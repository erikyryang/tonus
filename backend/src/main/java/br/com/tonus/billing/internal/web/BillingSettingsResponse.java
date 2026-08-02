package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.domain.BillingSettings;
import java.math.BigDecimal;

public record BillingSettingsResponse(BigDecimal multiPlanDiscountPercent) {

    public static BillingSettingsResponse from(BillingSettings settings) {
        return new BillingSettingsResponse(settings.getMultiPlanDiscountPercent());
    }
}
