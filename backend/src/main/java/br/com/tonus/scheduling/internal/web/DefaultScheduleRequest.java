package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record DefaultScheduleRequest(@NotNull Long studentId, @NotNull LocalTime time) {
}
