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
 * Mudança pontual: a aula daquele dia foi cancelada ou movida.
 *
 * O par (newDate, newTime) existe se e somente se for RESCHEDULED — invariante
 * garantida também por CHECK no banco, porque uma remarcação sem destino seria
 * uma aula que some sem reaparecer.
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
