package br.com.tonus.scheduling.internal;

/**
 * One default schedule per student. The database also guarantees this (uk_default_schedule_student);
 * this exception exists so the response is a 409 with a message, instead of a raw
 * integrity error.
 */
public class ScheduleAlreadyExistsException extends RuntimeException {

    public ScheduleAlreadyExistsException(Long studentId) {
        super("Aluno já possui horário padrão: " + studentId);
    }
}
