package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SubscriptionRequest(
        @NotNull Long studentId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal monthlyAmount,
        @NotNull @Min(1) @Max(31) Integer dueDay) {
}
