package br.com.tonus.scheduling;

import java.time.LocalDate;

/**
 * Feriado cadastrado. Suprime a agenda do dia inteiro, então o consumidor
 * precisa avisar todo mundo que tinha aula — quem são esses alunos é decisão
 * de quem consome, não deste evento.
 */
public record HolidayCreated(LocalDate date) {
}
