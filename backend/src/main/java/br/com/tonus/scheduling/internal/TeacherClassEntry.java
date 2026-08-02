package br.com.tonus.scheduling.internal;

import java.time.LocalTime;

/**
 * Uma aula da grade do professor: quem, quando e como falar com ele.
 *
 * Existe ao lado de {@link ScheduleEntry} em vez de ser um campo a mais nele
 * porque o telefone é dado pessoal e {@code ScheduleEntry} alimenta também a
 * agenda do ADMIN e a do aluno. Um campo a mais lá levaria o telefone de todo
 * mundo para dois caminhos que não têm finalidade para ele; aqui ele só existe
 * onde o professor atende o próprio aluno.
 */
public record TeacherClassEntry(Long studentId, String studentName, String studentPhone, LocalTime time) {
}
