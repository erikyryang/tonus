package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin(value = "0.01") BigDecimal monthlyPrice) {
}
