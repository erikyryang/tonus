package br.com.tonus.scheduling;

import java.time.LocalDate;

/**
 * Aula cancelada num dia específico. Consumido por notification (SPEC-004)
 * para avisar o aluno.
 *
 * Carrega id e data, nunca entidade: o consumidor busca o que precisar pela
 * fachada do registration.
 */
public record ClassCanceled(Long studentId, LocalDate date) {
}
