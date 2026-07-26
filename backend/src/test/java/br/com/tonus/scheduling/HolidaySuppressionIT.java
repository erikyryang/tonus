package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de aceite: feriado suprime **todos** os alunos do dia.
 *
 * É a regra que mais depende de o feriado ser global: não existe "feriado do
 * professor" nem "feriado da turma". Um único registro apaga o dia inteiro, e o
 * dia seguinte segue normal.
 */
class HolidaySuppressionIT extends AbstractSchedulingIntegrationTest {

    private static final String HOLIDAY = "2026-08-12";

    @Test
    void holidaySuppressesEveryStudentOfTheDay() throws Exception {
        long teacherA = createTeacher(unique("Professora Do Feriado A"));
        long teacherB = createTeacher(unique("Professor Do Feriado B"));
        long first = createStudent(unique("Aluno Feriado Um"), teacherA);
        long second = createStudent(unique("Aluno Feriado Dois"), teacherA);
        long third = createStudent(unique("Aluno Feriado Tres"), teacherB);
        createDefaultSchedule(first, "07:00");
        createDefaultSchedule(second, "07:00");
        createDefaultSchedule(third, "18:00");

        JsonNode before = daySchedule(HOLIDAY);
        assertThat(before.get("entries")).isNotEmpty();

        createHoliday(HOLIDAY, "Feriado do teste");

        JsonNode after = daySchedule(HOLIDAY);
        assertThat(after.get("entries")).isEmpty();
    }

    @Test
    void dayAfterHolidayIsUnaffected() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Vespera"));
        long studentId = createStudent(unique("Aluno Da Vespera"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        createHoliday("2026-08-13", "Outro feriado do teste");

        JsonNode nextDay = daySchedule("2026-08-14");

        assertThat(nextDay.get("entries").findValues("studentId"))
                .anySatisfy(node -> assertThat(node.asLong()).isEqualTo(studentId));
    }

    /**
     * Feriado apaga o dia inclusive para quem foi remarcado para ele: a aula
     * remarcada cai no mesmo dia sem aula que as demais.
     */
    @Test
    void holidayAlsoSuppressesReschedulesLandingOnIt() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Remarcacao No Feriado"));
        long studentId = createStudent(unique("Aluno Remarcado Pro Feriado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        rescheduleClass(studentId, MONDAY, "2026-08-19", "09:00");
        createHoliday("2026-08-19", "Feriado que engole remarcacao");

        assertThat(daySchedule("2026-08-19").get("entries")).isEmpty();
    }
}
