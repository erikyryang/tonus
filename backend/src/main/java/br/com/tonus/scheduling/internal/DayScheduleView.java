package br.com.tonus.scheduling.internal;

import java.time.LocalDate;
import java.util.List;

/**
 * The complete state of a day for whoever sees the whole studio.
 *
 * Announcement, schedule and cancellations go together because only together they explain the
 * day: an empty list alone does not distinguish "classes are suspended", "it is
 * Saturday" and "nobody has a class scheduled", and whoever received only the list would have to
 * guess which of the three.
 *
 * {@code announcement} is null on a day without an announcement. When it suspends
 * classes, both lists come empty — the whole day is suppressed. When it does not
 * suspend, it is just information alongside a normal schedule.
 */
public record DayScheduleView(LocalDate date,
                              Announcement announcement,
                              List<ScheduleEntry> entries,
                              List<CanceledEntry> cancellations) {

    /**
     * The announcement as the day sees it: what to say and whether it removes classes.
     * It is not the entity — the schedule does not expose a database row (see CLAUDE.md).
     */
    public record Announcement(String description, boolean suspendsClasses) {
    }
}
