package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A data de destino de uma remarcação precisa ser um dia em que a aula possa
 * acontecer.
 *
 * Nenhuma das duas regras é constraint de banco, e nem poderia ser: as duas
 * dependem de estado fora da linha — a data de hoje e a tabela `holiday`. Por
 * isso vivem no serviço, e por isso têm teste próprio.
 *
 * "Hoje" aqui é sempre {@link #MONDAY}, fixado por {@link SchedulingClockConfig}.
 */
class RescheduleDateRulesIT extends AbstractSchedulingIntegrationTest {

    @Test
    void rescheduleToPastDateIsRejected() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pro Passado");

        postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"%s","newTime":"08:00"}
                """.formatted(studentId, WEDNESDAY, LAST_FRIDAY))
                .andExpect(status().isBadRequest());
    }

    /**
     * Hoje é limite inferior inclusive: remarcar para daqui a pouco é legítimo,
     * e recusar obrigaria o ADMIN a esperar a virada do dia para registrar uma
     * troca combinada de manhã.
     */
    @Test
    void rescheduleToTodayIsAllowed() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pra Hoje");

        rescheduleClass(studentId, WEDNESDAY, MONDAY, "21:00");
    }

    @Test
    void rescheduleToFutureIsAllowed() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pro Futuro");

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");
    }

    @Test
    void rescheduleOntoRegisteredHolidayIsRejected() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pro Feriado");
        createHoliday("2026-09-21", "Feriado que bloqueia remarcacao");

        postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"2026-09-21","newTime":"08:00"}
                """.formatted(studentId, MONDAY))
                .andExpect(status().isBadRequest());
    }

    /**
     * A invariante é assumidamente parcial: o feriado cadastrado **depois** da
     * remarcação não desfaz nada — a aula só some da agenda daquele dia, porque
     * a expansão suprime o dia inteiro. A checagem serve para o ADMIN descobrir
     * na hora, não para garantir consistência que este modelo não tem como dar.
     */
    @Test
    void holidayRegisteredAfterRescheduleIsNotRejectedRetroactively() throws Exception {
        long studentId = seedStudent("Aluno Do Feriado Posterior");
        rescheduleClass(studentId, MONDAY, "2026-09-22", "08:00");

        createHoliday("2026-09-22", "Feriado cadastrado depois");
    }

    /**
     * Cancelamento não tem data de destino, então nenhuma das duas regras se
     * aplica: cancelar uma aula que já passou é registro de fato consumado.
     */
    @Test
    void cancellationInThePastIsAllowed() throws Exception {
        long studentId = seedStudent("Aluno Que Faltou Semana Passada");

        cancelClass(studentId, LAST_FRIDAY);
    }

    private long seedStudent(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor Das Regras De Data")));
    }
}
