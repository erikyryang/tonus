package br.com.tonus.scheduling.internal.domain;

import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One-off change: that day's class was canceled or moved.
 *
 * The pair (newDate, newTime) exists if and only if it is RESCHEDULED — an invariant
 * also guaranteed by a CHECK in the database, because a reschedule with no destination would be
 * a class that disappears without reappearing.
 */
@Entity
@Table(name = "schedule_exception")
public class ScheduleException extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Column(name = "new_date")
    private LocalDate newDate;

    @Column(name = "new_time")
    private LocalTime newTime;

    protected ScheduleException() {
    }

    public static ScheduleException cancellation(Long studentId, LocalDate date) {
        ScheduleException exception = new ScheduleException();
        exception.studentId = studentId;
        exception.date = date;
        exception.type = ExceptionType.CANCELED;
        return exception;
    }

    public static ScheduleException reschedule(Long studentId, LocalDate date, LocalDate newDate, LocalTime newTime) {
        ScheduleException exception = new ScheduleException();
        exception.studentId = studentId;
        exception.date = date;
        exception.type = ExceptionType.RESCHEDULED;
        exception.newDate = newDate;
        exception.newTime = newTime;
        return exception;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDate getDate() {
        return date;
    }

    public ExceptionType getType() {
        return type;
    }

    public LocalDate getNewDate() {
        return newDate;
    }

    public LocalTime getNewTime() {
        return newTime;
    }
}
