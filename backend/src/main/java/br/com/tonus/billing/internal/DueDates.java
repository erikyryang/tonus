package br.com.tonus.billing.internal;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The due date of a billing period based on the student's registration day.
 *
 * Lives outside the service because it's a pure rule, with no database and no
 * clock: it used to be a method of {@code Subscription} and couldn't die
 * along with it (SPEC-012). The clamping to the last day of the month is the
 * part that gets forgotten — day 31 in February — and it's what the unit test
 * guards against.
 */
public final class DueDates {

    private DueDates() {
    }

    public static LocalDate forMonth(LocalDate registration, YearMonth month) {
        return month.atDay(Math.min(registration.getDayOfMonth(), month.lengthOfMonth()));
    }
}
