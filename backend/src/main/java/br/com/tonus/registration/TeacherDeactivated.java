package br.com.tonus.registration;

/**
 * Teacher left the studio. Consumed by auth to revoke access of the linked
 * user. At-least-once delivery: the listener is idempotent.
 */
public record TeacherDeactivated(Long teacherId) {
}
