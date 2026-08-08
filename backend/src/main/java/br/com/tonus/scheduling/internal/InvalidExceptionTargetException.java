package br.com.tonus.scheduling.internal;

/**
 * The pair (new date, new time) exists if and only if it is a reschedule.
 * Same invariant as the CHECK ck_schedule_exception_target.
 */
public class InvalidExceptionTargetException extends RuntimeException {

    public InvalidExceptionTargetException(String message) {
        super(message);
    }
}
