package br.com.tonus.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Aula movida de dia. Leva a data original junto porque quem avisa o aluno
 * precisa dizer de onde para onde — "sua aula foi remarcada" sem a origem não
 * informa nada.
 */
public record ClassRescheduled(Long studentId, LocalDate originalDate, LocalDate newDate, LocalTime newTime) {
}
