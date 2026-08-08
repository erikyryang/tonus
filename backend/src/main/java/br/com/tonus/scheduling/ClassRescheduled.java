package br.com.tonus.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Class moved to a different day. Carries the original date along because whoever notifies the student
 * needs to say from where to where — "your class was rescheduled" without the origin
 * does not tell anything.
 */
public record ClassRescheduled(Long studentId, LocalDate originalDate, LocalDate newDate, LocalTime newTime) {
}
