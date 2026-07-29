package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.CanceledEntry;
import br.com.tonus.scheduling.internal.DayScheduleView;
import br.com.tonus.scheduling.internal.ScheduleEntry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A agenda de um dia já expandida — o cliente não recebe template, exceção e
 * feriado para combinar por conta própria. Se recebesse, cada consumidor
 * reimplementaria a regra, e um deles erraria.
 *
 * Pela mesma razão o dia carrega o estado inteiro: a descrição do feriado (que
 * explica por que não há aula) e os cancelamentos (que a expansão remove por
 * definição). Sem isso a tela teria de deduzir "está vazio, deve ser feriado" —
 * inventando um motivo que ela não tem como saber.
 */
public record DayScheduleResponse(LocalDate date,
                                  String holiday,
                                  List<Entry> entries,
                                  List<Cancellation> cancellations) {

    /** Agenda de quem só enxerga a própria fatia: sem feriado e sem cancelamento. */
    public static DayScheduleResponse of(LocalDate date, List<ScheduleEntry> entries) {
        return of(new DayScheduleView(date, null, entries, List.of()));
    }

    public static DayScheduleResponse of(DayScheduleView day) {
        return new DayScheduleResponse(day.date(), day.holiday(),
                day.entries().stream().map(Entry::from).toList(),
                day.cancellations().stream().map(Cancellation::from).toList());
    }

    public record Entry(Long studentId,
                        String studentName,
                        Long teacherId,
                        String teacherName,
                        LocalTime time,
                        ScheduleEntry.Origin origin,
                        LocalDate originDate,
                        Long exceptionId) {

        static Entry from(ScheduleEntry entry) {
            return new Entry(entry.studentId(), entry.studentName(), entry.teacherId(), entry.teacherName(),
                    entry.time(), entry.origin(), entry.originDate(), entry.exceptionId());
        }
    }

    public record Cancellation(Long exceptionId,
                               Long studentId,
                               String studentName,
                               Long teacherId,
                               String teacherName,
                               LocalTime time) {

        static Cancellation from(CanceledEntry entry) {
            return new Cancellation(entry.exceptionId(), entry.studentId(), entry.studentName(),
                    entry.teacherId(), entry.teacherName(), entry.time());
        }
    }
}
