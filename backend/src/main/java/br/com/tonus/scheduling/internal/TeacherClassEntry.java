package br.com.tonus.scheduling.internal;

import java.time.LocalTime;

/**
 * A class from the teacher's grid: who, when and how to speak with them.
 *
 * Exists alongside {@link ScheduleEntry} instead of being an extra field on it
 * because phone is personal data and {@code ScheduleEntry} also feeds the
 * ADMIN's schedule and the student's. An extra field there would carry everyone's
 * phone to two paths that have no purpose for it; here it only exists
 * where the teacher attends that same student.
 */
public record TeacherClassEntry(Long studentId, String studentName, String studentPhone, LocalTime time) {
}
