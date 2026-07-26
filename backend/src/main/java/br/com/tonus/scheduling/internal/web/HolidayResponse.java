package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.domain.Holiday;
import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate date, String description) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getDescription());
    }
}
