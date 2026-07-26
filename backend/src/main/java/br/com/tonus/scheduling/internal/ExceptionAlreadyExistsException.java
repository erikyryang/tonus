package br.com.tonus.scheduling.internal;

import java.time.LocalDate;

public class ExceptionAlreadyExistsException extends RuntimeException {

    public ExceptionAlreadyExistsException(Long studentId, LocalDate date) {
        super("Aluno %d já possui exceção em %s".formatted(studentId, date));
    }
}
