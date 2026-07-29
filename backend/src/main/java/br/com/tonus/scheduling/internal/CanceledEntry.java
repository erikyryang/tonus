package br.com.tonus.scheduling.internal;

import java.time.LocalTime;

/**
 * Uma aula que o dia deixou de ter por cancelamento.
 *
 * A expansão da agenda a remove por definição, e é justamente por isso que ela
 * precisa existir à parte: o ADMIN só percebe um cancelamento marcado por
 * engano se continuar vendo o que cancelou — e é pelo {@code exceptionId} que
 * ele desfaz.
 *
 * {@code time} é a hora do horário padrão do aluno e pode ser nula: nada exige
 * horário padrão para cancelar uma aula.
 */
public record CanceledEntry(Long exceptionId,
                            Long studentId,
                            String studentName,
                            Long teacherId,
                            String teacherName,
                            LocalTime time) {
}
