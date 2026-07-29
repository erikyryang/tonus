package br.com.tonus.scheduling.internal;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Uma aula de um dia já expandida. Não é entidade: não existe linha no banco
 * que corresponda a isto — é o resultado de template − feriado − exceções +
 * remarcações, calculado a cada consulta.
 *
 * {@code originDate} e {@code exceptionId} são nulos quando a aula veio do
 * template. Quando veio de uma remarcação, dizem de que dia ela saiu e qual
 * exceção a trouxe — sem os dois, quem exibe a agenda não teria como mostrar a
 * procedência da aula nem como desfazê-la.
 */
public record ScheduleEntry(Long studentId,
                            String studentName,
                            Long teacherId,
                            String teacherName,
                            LocalTime time,
                            Origin origin,
                            LocalDate originDate,
                            Long exceptionId) {

    /** De onde a aula veio: do horário recorrente ou de uma remarcação. */
    public enum Origin {
        TEMPLATE,
        RESCHEDULED
    }
}
