package br.com.tonus.scheduling.internal;

import java.time.LocalDate;

public class HolidayAlreadyExistsException extends RuntimeException {

    public HolidayAlreadyExistsException(LocalDate date) {
        super("Já existe feriado em " + date);
    }
}
