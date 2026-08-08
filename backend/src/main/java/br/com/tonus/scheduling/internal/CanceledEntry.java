package br.com.tonus.scheduling.internal;

import java.time.LocalTime;

/**
 * A class that the day no longer has due to cancellation.
 *
 * The schedule expansion removes it by definition, and that is exactly why it
 * needs to exist separately: the ADMIN only notices a cancellation marked by
 * mistake if they keep seeing what was canceled — and it is by the {@code exceptionId} that
 * they undo it.
 *
 * {@code time} is the time from the student's default schedule and can be null: nothing requires a
 * default schedule to cancel a class.
 */
public record CanceledEntry(Long exceptionId,
                            Long studentId,
                            String studentName,
                            Long teacherId,
                            String teacherName,
                            LocalTime time) {
}
