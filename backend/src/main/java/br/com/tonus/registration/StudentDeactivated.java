package br.com.tonus.registration;

/**
 * Student left the studio. Consumed by auth to revoke access of the linked
 * user. At-least-once delivery: the listener is idempotent.
 */
public record StudentDeactivated(Long studentId) {
}
