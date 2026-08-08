package br.com.tonus.billing.internal;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure due date resolution: registration day -> competence period date. No
 * database and no Spring, because it is domain logic without side effects.
 *
 * It inherited the role of {@code SubscriptionDueDateTest}, which died with
 * the subscription. The rule did not die with it — it just changed home
 * (SPEC-012).
 */
class DueDatesTest {

    @Test
    void resolvesToTheSameDayWhenMonthHasEnoughDays() {
        LocalDate registration = LocalDate.of(2025, 1, 10);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2026, 7))).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    /**
     * Acceptance criterion: a day that does not exist in the month (31 in
     * February) falls on the last day. 2026 is not a leap year; February has
     * 28 days.
     */
    @Test
    void rollsOverToLastDayOfShortMonth() {
        LocalDate registration = LocalDate.of(2025, 1, 31);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    /** And in a leap year the last day is 29, not 28. */
    @Test
    void respectsLeapYear() {
        LocalDate registration = LocalDate.of(2025, 1, 31);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2028, 2))).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
