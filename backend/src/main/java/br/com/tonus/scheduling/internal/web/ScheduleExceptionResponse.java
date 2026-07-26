package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.domain.ExceptionType;
import br.com.tonus.scheduling.internal.domain.ScheduleException;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleExceptionResponse(Long id,
                                        Long studentId,
                                        LocalDate date,
                                        ExceptionType type,
                                        LocalDate newDate,
                                        LocalTime newTime) {

    public static ScheduleExceptionResponse from(ScheduleException exception) {
        return new ScheduleExceptionResponse(exception.getId(), exception.getStudentId(), exception.getDate(),
                exception.getType(), exception.getNewDate(), exception.getNewTime());
    }
}
