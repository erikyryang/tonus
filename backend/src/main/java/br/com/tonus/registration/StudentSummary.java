package br.com.tonus.registration;

/**
 * The teacher's name comes along because whoever consumes the list almost always
 * needs it: the day's schedule groups by teacher + time, and the group appears
 * on screen with the name, not the id. Without this, each consumer would make a query per
 * student to resolve the same id — N+1 on the hottest screen of the system.
 */
public record StudentSummary(Long studentId, String name, Long teacherId, String teacherName, boolean active) {
}
