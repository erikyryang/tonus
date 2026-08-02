package br.com.tonus.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O comunicado que suspende as aulas apaga o dia inteiro; o que não suspende
 * não encosta na agenda.
 *
 * A supressão continua dependendo de o comunicado ser global: não existe
 * "comunicado do professor" nem "da turma". Um único registro apaga o dia
 * inteiro, e o dia seguinte segue normal.
 */
class AnnouncementSuppressionIT extends AbstractSchedulingIntegrationTest {

    private static final String SUSPENDED_DAY = "2026-08-12";

    /**
     * Comunicado é global e o banco é compartilhado pela suíte: cada classe usa
     * datas próprias, senão o dia de uma apaga a agenda da outra. Esta é uma
     * quarta-feira que ninguém mais toca.
     */
    private static final String ANNOUNCED_DAY = "2026-10-21";

    @Test
    void suspendingAnnouncementSuppressesEveryStudentOfTheDay() throws Exception {
        long teacherA = createTeacher(unique("Professora Do Comunicado A"));
        long teacherB = createTeacher(unique("Professor Do Comunicado B"));
        long first = createStudent(unique("Aluno Comunicado Um"), teacherA);
        long second = createStudent(unique("Aluno Comunicado Dois"), teacherA);
        long third = createStudent(unique("Aluno Comunicado Tres"), teacherB);
        createDefaultSchedule(first, "07:00");
        createDefaultSchedule(second, "07:00");
        createDefaultSchedule(third, "18:00");

        JsonNode before = daySchedule(SUSPENDED_DAY);
        assertThat(before.get("entries")).isNotEmpty();

        createAnnouncement(SUSPENDED_DAY, "Feriado do teste", true);

        JsonNode after = daySchedule(SUSPENDED_DAY);
        assertThat(after.get("entries")).isEmpty();
        assertThat(after.get("announcement").get("description").asText()).isEqualTo("Feriado do teste");
        assertThat(after.get("announcement").get("suspendsClasses").asBoolean()).isTrue();
    }

    /**
     * O comunicado que só informa mantém a agenda de pé. É a razão de o
     * conceito ter deixado de ser "feriado": avisar todo mundo de alguma coisa
     * não pode custar o dia de aula.
     */
    @Test
    void announcementWithoutSuspensionLeavesTheScheduleIntact() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Aviso"));
        long studentId = createStudent(unique("Aluno Do Aviso"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        createAnnouncement(ANNOUNCED_DAY, "Obra na rua, entrada pelos fundos", false);

        JsonNode day = daySchedule(ANNOUNCED_DAY);
        assertThat(day.get("entries").findValues("studentId"))
                .anySatisfy(node -> assertThat(node.asLong()).isEqualTo(studentId));
        assertThat(day.get("announcement").get("description").asText())
                .isEqualTo("Obra na rua, entrada pelos fundos");
        assertThat(day.get("announcement").get("suspendsClasses").asBoolean()).isFalse();
    }

    /** O dia sem comunicado nenhum não carrega comunicado nenhum. */
    @Test
    void dayWithoutAnnouncementCarriesNone() throws Exception {
        assertThat(daySchedule("2029-01-10").get("announcement").isNull()).isTrue();
    }

    @Test
    void dayAfterSuspendedDayIsUnaffected() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Vespera"));
        long studentId = createStudent(unique("Aluno Da Vespera"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        createAnnouncement("2026-08-13", "Outro feriado do teste", true);

        JsonNode nextDay = daySchedule("2026-08-14");

        assertThat(nextDay.get("entries").findValues("studentId"))
                .anySatisfy(node -> assertThat(node.asLong()).isEqualTo(studentId));
    }

    /**
     * A suspensão apaga o dia inclusive para quem foi remarcado para ele: a
     * aula remarcada cai no mesmo dia sem aula que as demais.
     */
    @Test
    void suspensionAlsoSuppressesReschedulesLandingOnIt() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Remarcacao No Feriado"));
        long studentId = createStudent(unique("Aluno Remarcado Pro Feriado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        rescheduleClass(studentId, MONDAY, "2026-08-19", "09:00");
        createAnnouncement("2026-08-19", "Feriado que engole remarcacao", true);

        assertThat(daySchedule("2026-08-19").get("entries")).isEmpty();
    }
}
