package br.com.tonus.scheduling;

import java.time.LocalDate;

/**
 * Class canceled on a specific day. Consumed by notification (SPEC-004)
 * to notify the student.
 *
 * Carries id and date, never entity: the consumer looks up whatever it needs via the
 * registration facade.
 */
public record ClassCanceled(Long studentId, LocalDate date) {
}
