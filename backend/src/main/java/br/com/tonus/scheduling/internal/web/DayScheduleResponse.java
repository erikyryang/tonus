package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.ScheduleEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A agenda de um dia já expandida — o cliente não recebe template, exceção e
 * feriado para combinar por conta própria. Se recebesse, cada consumidor
 * reimplementaria a regra, e um deles erraria.
 */
public record DayScheduleResponse(LocalDate date, List<Entry> entries) {

    public static DayScheduleResponse of(LocalDate date, List<ScheduleEntry> entries) {
        return new DayScheduleResponse(date, entries.stream().map(Entry::from).toList());
    }

    public record Entry(Long studentId,
                        String studentName,
                        Long teacherId,
                        LocalTime time,
                        ScheduleEntry.Origin origin) {

        static Entry from(ScheduleEntry entry) {
            return new Entry(entry.studentId(), entry.studentName(), entry.teacherId(),
                    entry.time(), entry.origin());
        }
    }
}
