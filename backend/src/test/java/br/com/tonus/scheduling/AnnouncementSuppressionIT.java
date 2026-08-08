package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The announcement that suspends classes erases the whole day; the one that does not suspend
 * does not touch the schedule.
 *
 * Suppression still depends on the announcement being global: there is no
 * "teacher's announcement" nor "class's announcement". A single record erases the whole
 * day, and the following day remains normal.
 */
class AnnouncementSuppressionIT extends AbstractSchedulingIntegrationTest {

    private static final String SUSPENDED_DAY = "2026-08-12";

    /**
     * Announcement is global and the database is shared by the suite: each class uses
     * its own dates, otherwise one's day erases the other's schedule. This is a
     * Wednesday that no one else touches.
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
     * The announcement that only informs keeps the schedule standing. It is the reason the
     * concept stopped being "holiday": notifying everyone about something
     * must not cost the class day.
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

    /** A day with no announcement at all carries no announcement at all. */
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
     * The suspension erases the day even for whoever was rescheduled into it: the
     * rescheduled class falls on the same no-class day as the others.
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
