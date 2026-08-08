package br.com.tonus.registration;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract of the registration module for the other modules (scheduling, billing,
 * notification). Synchronous query only — side effect between modules is
 * via event.
 */
public interface RegistrationFacade {

    Optional<StudentContact> findStudentContact(Long studentId);

    boolean studentExists(Long studentId);

    boolean teacherExists(Long teacherId);

    /**
     * Student exists and is still at the studio. Separate from {@link #studentExists}
     * because the caller needs to distinguish "does not exist" from "left" — the two
     * situations lead to different messages and decisions.
     */
    boolean studentIsActive(Long studentId);

    boolean teacherIsActive(Long teacherId);

    /**
     * Names of several students at once, indexed by id.
     *
     * Exists for listings that show student row by row (invoices, SPEC-008).
     * {@link #findStudentContact} does not serve: it would be a per-row query and
     * would hand over phone — personal data — to whoever only needs the name. Ids with no
     * matching student simply do not appear in the map.
     *
     * Includes deactivated student on purpose: whoever left the studio still keeps
     * invoice history (SPEC-002 has no DELETE), and the screen still needs to
     * say who the charge belongs to.
     */
    Map<Long, String> findStudentNames(Collection<Long> studentIds);

    /**
     * Phones of several students at once, indexed by id.
     *
     * Exists separately from {@link #findStudentNames} because phone is personal
     * data: whoever only needs the name should not get it as a bonus. Whoever calls
     * here declares the purpose — the teacher's grid, who attends that student.
     *
     * Batched and not per student for the same reason as the names: the grid is
     * traversed entirely at once, and a per-row query would be N+1.
     *
     * Never log the returned value.
     */
    Map<Long, String> findStudentPhones(Collection<Long> studentIds);

    /**
     * The day the student joined the studio.
     *
     * Billing needs this to fix the monthly fee due date on the day of the
     * month they registered (SPEC-004). It is a date, not an instant: the
     * caller wants the calendar day in the studio's timezone, and the
     * registration time means nothing for a monthly fee.
     *
     * Empty when the student does not exist — the decision of what to do with
     * that is up to the caller.
     */
    Optional<LocalDate> findRegistrationDate(Long studentId);

    /**
     * The registration dates of several students at once, indexed by id.
     *
     * Exists for the daily billing job, which resolves each invoice's due date
     * by the registration day (SPEC-004) and runs over the whole studio:
     * asking student by student would be N+1 in the only place in the system that
     * works with everyone at once. Ids with no student do not appear in the map.
     */
    Map<Long, LocalDate> findRegistrationDates(Collection<Long> studentIds);

    /**
     * A single student, with name and teacher — what the schedule needs to build
     * the row of a class.
     *
     * Exists alongside {@link #findActiveStudents()} because the questions are
     * different: the studio schedule wants everyone, a student's schedule
     * wants one. Answering the second with the first means loading the whole
     * studio just to discard everything but one row, and it is the student — on the
     * phone, on the screen they open the most — who pays for that.
     *
     * Also returns the inactive student: the caller is the one who decides what to do
     * with {@link StudentSummary#active()}, and silently hiding it
     * would turn "left the studio" into "does not exist".
     */
    Optional<StudentSummary> findStudentSummary(Long studentId);

    List<StudentSummary> findActiveStudents();

    List<StudentSummary> findStudentsByTeacher(Long teacherId);
}
