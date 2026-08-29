package br.com.tonus.scheduling.internal.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScheduleExceptionRepository
        extends JpaRepository<ScheduleException, Long>, JpaSpecificationExecutor<ScheduleException> {

    boolean existsByStudentIdAndDate(Long studentId, LocalDate date);

    /** What leaves D's schedule: every exception marked on that day. */
    List<ScheduleException> findByDate(LocalDate date);

    /** What enters D's schedule: the reschedules whose destination is D. */
    List<ScheduleException> findByNewDate(LocalDate newDate);

    /** Whether a reschedule lands on D for this student — a class exists there. */
    boolean existsByNewDateAndStudentId(LocalDate newDate, Long studentId);

    /**
     * The exceptions of a student in an interval — both ends at once.
     *
     * `date` is where the class left from and `newDate` is where it went to, and the
     * student's window needs both: a class could have left from inside the window
     * to outside it, or entered from outside to inside. Querying by interval instead
     * of by day is what makes the seven-day window cost one query instead
     * of seven.
     */
    List<ScheduleException> findByStudentIdAndDateBetween(Long studentId, LocalDate from, LocalDate to);

    List<ScheduleException> findByStudentIdAndNewDateBetween(Long studentId, LocalDate from, LocalDate to);

    /**
     * Used when deleting the student's default schedule. The exception is indexed by
     * (student_id, date), with no link to the schedule's id — without this
     * cleanup, an old exception would survive the deletion and would go back to
     * suppressing a new schedule created later for the same student on the same day.
     */
    void deleteByStudentId(Long studentId);
}
