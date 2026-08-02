package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.NotNull;

public record PlanEnrollmentRequest(@NotNull Long studentId, @NotNull Long planId) {
}
