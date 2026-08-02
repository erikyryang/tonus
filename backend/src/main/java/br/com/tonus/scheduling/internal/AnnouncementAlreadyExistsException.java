package br.com.tonus.scheduling.internal;

import java.time.LocalDate;

public class AnnouncementAlreadyExistsException extends RuntimeException {

    public AnnouncementAlreadyExistsException(LocalDate date) {
        super("Já existe comunicado em " + date);
    }
}
