package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.CanceledEntry;
import br.com.tonus.scheduling.internal.DayScheduleView;
import br.com.tonus.scheduling.internal.ScheduleEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The already expanded schedule of a day — the client does not receive template, exception and
 * announcement to combine on its own. If it did, each consumer would
 * reimplement the rule, and one of them would get it wrong.
 *
 * For the same reason the day carries the whole state: the announcement (which explains
 * why there is no class, or notifies about something on a day that has class) and the
 * cancellations (which the expansion removes by definition). Without this the screen would have to
 * deduce "it is empty, must be a holiday" — inventing a reason it has no
 * way of knowing.
 */
public record DayScheduleResponse(LocalDate date,
                                  Announcement announcement,
                                  List<Entry> entries,
                                  List<Cancellation> cancellations) {

    public static DayScheduleResponse of(DayScheduleView day) {
        return new DayScheduleResponse(day.date(), Announcement.from(day.announcement()),
                day.entries().stream().map(Entry::from).toList(),
                day.cancellations().stream().map(Cancellation::from).toList());
    }

    /** Null on a day without an announcement. */
    public record Announcement(String description, boolean suspendsClasses) {

        static Announcement from(DayScheduleView.Announcement announcement) {
            return announcement == null
                    ? null
                    : new Announcement(announcement.description(), announcement.suspendsClasses());
        }
    }

    public record Entry(Long studentId,
                        String studentName,
                        Long teacherId,
                        String teacherName,
                        LocalTime time,
                        ScheduleEntry.Origin origin,
                        LocalDate originDate,
                        Long exceptionId) {

        static Entry from(ScheduleEntry entry) {
            return new Entry(entry.studentId(), entry.studentName(), entry.teacherId(), entry.teacherName(),
                    entry.time(), entry.origin(), entry.originDate(), entry.exceptionId());
        }
    }

    public record Cancellation(Long exceptionId,
                               Long studentId,
                               String studentName,
                               Long teacherId,
                               String teacherName,
                               LocalTime time) {

        static Cancellation from(CanceledEntry entry) {
            return new Cancellation(entry.exceptionId(), entry.studentId(), entry.studentName(),
                    entry.teacherId(), entry.teacherName(), entry.time());
        }
    }
}
