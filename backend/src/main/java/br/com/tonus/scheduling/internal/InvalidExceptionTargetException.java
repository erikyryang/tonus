package br.com.tonus.scheduling.internal;

/**
 * O par (nova data, nova hora) existe se e somente se for remarcação.
 * Mesma invariante do CHECK ck_schedule_exception_target.
 */
public class InvalidExceptionTargetException extends RuntimeException {

    public InvalidExceptionTargetException(String message) {
        super(message);
    }
}
