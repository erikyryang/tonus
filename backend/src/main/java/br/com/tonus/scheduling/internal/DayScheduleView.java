package br.com.tonus.scheduling.internal;

import java.time.LocalDate;
import java.util.List;

/**
 * O estado completo de um dia para quem enxerga o estúdio inteiro.
 *
 * Feriado, agenda e cancelamentos andam juntos porque só juntos explicam o dia:
 * uma lista vazia sozinha não distingue "é feriado", "é sábado" e "ninguém tem
 * aula marcada", e quem recebesse só a lista teria de adivinhar qual dos três.
 *
 * {@code holiday} é nulo fora de feriado; num feriado as duas listas são vazias,
 * porque o dia inteiro está suprimido.
 */
public record DayScheduleView(LocalDate date,
                              String holiday,
                              List<ScheduleEntry> entries,
                              List<CanceledEntry> cancellations) {
}
