package br.com.tonus.scheduling;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The destination date of a reschedule must be a day on which a class can
 * happen.
 *
 * Neither rule is a database constraint, and could not be: both depend on
 * state outside the row — today's date and the `announcement` table. That is
 * why they live in the service, and why they have their own test.
 *
 * "Today" here is always {@link #MONDAY}, fixed by {@link SchedulingClockConfig}.
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
      * Today is an inclusive lower bound: rescheduling to later today is
      * legitimate, and refusing would force the ADMIN to wait for the day to
      * turn to register a change agreed on in the morning.
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
    void rescheduleOntoSuspendedDayIsRejected() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pro Feriado");
        createAnnouncement("2026-09-21", "Feriado que bloqueia remarcacao", true);

        postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"2026-09-21","newTime":"08:00"}
                """.formatted(studentId, MONDAY))
                .andExpect(status().isBadRequest());
    }

     /**
      * An announcement that does not suspend blocks nothing: the day still has
      * class, and refusing the reschedule because of a notice would confuse "I
      * announced something about this day" with "this day does not exist".
      */
    @Test
    void rescheduleOntoAnnouncedButWorkingDayIsAllowed() throws Exception {
        long studentId = seedStudent("Aluno Remarcado Pro Dia Avisado");
        createAnnouncement("2026-09-23", "Reforma da recepcao", false);

        postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"2026-09-23","newTime":"08:00"}
                """.formatted(studentId, MONDAY))
                .andExpect(status().isCreated());
    }

     /**
      * The invariant is admittedly partial: a suspension registered **after**
      * the reschedule undoes nothing — the class only disappears from that
      * day's schedule, because the expansion suppresses the whole day. The
      * check exists for the ADMIN to find out right away, not to guarantee
      * consistency this model has no way to provide.
      */
    @Test
    void suspensionRegisteredAfterRescheduleIsNotRejectedRetroactively() throws Exception {
        long studentId = seedStudent("Aluno Do Feriado Posterior");
        rescheduleClass(studentId, MONDAY, "2026-09-22", "08:00");

        createAnnouncement("2026-09-22", "Feriado cadastrado depois", true);
    }

     /**
      * A cancellation has no destination date, so neither rule applies:
      * canceling a class that already happened is recording a fait accompli.
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
