package br.com.tonus.scheduling.internal;

/**
 * The target date of a reschedule needs to be a day on which the class can
 * actually happen: not in the past and not on an already registered holiday.
 *
 * Neither of the two is a database constraint, nor could it be — both depend
 * on state external to the row (today's date, the announcement table).
 */
public class InvalidRescheduleDateException extends RuntimeException {

    public InvalidRescheduleDateException(String message) {
        super(message);
    }
}
