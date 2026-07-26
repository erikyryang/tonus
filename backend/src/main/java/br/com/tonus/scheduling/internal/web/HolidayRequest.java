package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record HolidayRequest(@NotNull LocalDate date, @NotBlank String description) {
}
