package br.com.tonus.scheduling.internal;

import java.time.LocalDate;
import java.util.List;

/**
 * O estado completo de um dia para quem enxerga o estúdio inteiro.
 *
 * Comunicado, agenda e cancelamentos andam juntos porque só juntos explicam o
 * dia: uma lista vazia sozinha não distingue "as aulas estão suspensas", "é
 * sábado" e "ninguém tem aula marcada", e quem recebesse só a lista teria de
 * adivinhar qual dos três.
 *
 * {@code announcement} é nulo no dia sem comunicado. Quando ele suspende as
 * aulas, as duas listas vêm vazias — o dia inteiro está suprimido. Quando não
 * suspende, ele é só informação ao lado de uma agenda normal.
 */
public record DayScheduleView(LocalDate date,
                              Announcement announcement,
                              List<ScheduleEntry> entries,
                              List<CanceledEntry> cancellations) {

    /**
     * O comunicado como o dia o enxerga: o que dizer e se some com as aulas.
     * Não é a entidade — a agenda não expõe linha de banco (ver CLAUDE.md).
     */
    public record Announcement(String description, boolean suspendsClasses) {
    }
}
