package br.com.tonus.scheduling.internal;

import java.time.LocalTime;

/**
 * Uma aula de um dia já expandida. Não é entidade: não existe linha no banco
 * que corresponda a isto — é o resultado de template − feriado − exceções +
 * remarcações, calculado a cada consulta.
 */
public record ScheduleEntry(Long studentId, String studentName, Long teacherId, LocalTime time, Origin origin) {

    /** De onde a aula veio: do horário recorrente ou de uma remarcação. */
    public enum Origin {
        TEMPLATE,
        RESCHEDULED
    }
}
