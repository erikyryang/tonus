package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.domain.ExceptionType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleExceptionRequest(@NotNull Long studentId,
                                       @NotNull LocalDate date,
                                       @NotNull ExceptionType type,
                                       LocalDate newDate,
                                       LocalTime newTime) {
}
