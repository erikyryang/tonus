package br.com.tonus.scheduling.internal;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An already expanded class for a day. It is not an entity: there is no database row
 * that corresponds to this — it is the result of template - holiday - exceptions +
 * reschedules, computed on every query.
 *
 * {@code originDate} and {@code exceptionId} are null when the class came from the
 * template. When it came from a reschedule, they say what day it came from and which
 * exception brought it — without both, whoever displays the schedule would have no way to show the
 * origin of the class nor how to undo it.
 */
public record ScheduleEntry(Long studentId,
                            String studentName,
                            Long teacherId,
                            String teacherName,
                            LocalTime time,
                            Origin origin,
                            LocalDate originDate,
                            Long exceptionId) {

    /** Where the class came from: the recurring schedule or a reschedule. */
    public enum Origin {
        TEMPLATE,
        RESCHEDULED
    }
}
