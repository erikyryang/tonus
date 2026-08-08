package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the day's schedule needs to carry for the reception panel of SPEC-007.
 *
 * The screen cannot rebuild the day's rule on its own: if it crossed
 * announcement, exception and template in the browser, there would be two
 * implementations of the same expansion and one would fall behind. So the day
 * already comes with the teacher's name (to group), the reschedule's origin
 * (for the badge), the exception id (to undo), the day's announcement (for
 * the banner) and the cancellations — which the schedule hides by definition
 * and the ADMIN needs to see in order to undo them.
 */
class DayScheduleForAdminPanelIT extends AbstractSchedulingIntegrationTest {

    @Test
    void entryCarriesTeacherNameForGrouping() throws Exception {
        String teacherName = unique("Professora Do Painel");
        long teacherId = createTeacher(teacherName);
        long studentId = createStudent(unique("Aluna Do Painel"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        JsonNode entry = entryOf(daySchedule(MONDAY), studentId);

        assertThat(entry.get("teacherName").asText()).isEqualTo(teacherName);
    }

     /**
      * The "rescheduled from dd/MM" badge only exists if the day says where the
      * class came from: without the origin date, the screen would show the class
      * as if it came from the student's default schedule.
      *
      * The student appears TWICE on Wednesday — at 07:00 from the template,
      * which still holds, and at 09:00 from the reschedule. It is not
      * duplication: they lost Monday's class and gained an extra one on
      * Wednesday. That is why the test selects by time, not just by student.
      */
    @Test
    void rescheduledEntryCarriesItsOriginAndExceptionId() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Origem"));
        long studentId = createStudent(unique("Aluno Remarcado Pro Painel"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        long exceptionId = rescheduleClass(studentId, MONDAY, WEDNESDAY, "09:00");

        JsonNode day = daySchedule(WEDNESDAY);
        JsonNode entry = entryAt(day, studentId, "09:00");

        assertThat(entry.get("origin").asText()).isEqualTo("RESCHEDULED");
        assertThat(entry.get("originDate").asText()).isEqualTo(MONDAY);
        assertThat(entry.get("exceptionId").asLong()).isEqualTo(exceptionId);

        assertThat(entryAt(day, studentId, "07:00").get("origin").asText()).isEqualTo("TEMPLATE");
    }

    @Test
    void templateEntryHasNoOriginNorException() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Template Puro"));
        long studentId = createStudent(unique("Aluno Do Template Puro"), teacherId);
        createDefaultSchedule(studentId, "08:00");

        JsonNode entry = entryOf(daySchedule(MONDAY), studentId);

        assertThat(entry.get("originDate").isNull()).isTrue();
        assertThat(entry.get("exceptionId").isNull()).isTrue();
    }

     /**
      * Date exclusive to this test. {@code announcement.date} is UNIQUE and the
      * database is shared across the whole run: reusing another IT's date makes
      * whichever runs second get a 409 — and which one breaks depends on the
      * order.
      */
    @Test
    void suspendedDayCarriesItsAnnouncement() throws Exception {
        String date = "2026-10-28";
        createAnnouncement(date, "Feriado do painel", true);

        JsonNode day = daySchedule(date);

        assertThat(day.get("announcement").get("description").asText()).isEqualTo("Feriado do painel");
        assertThat(day.get("entries")).isEmpty();
        assertThat(day.get("cancellations")).isEmpty();
    }

    @Test
    void ordinaryDayHasNoAnnouncement() throws Exception {
        assertThat(daySchedule(TUESDAY).get("announcement").isNull()).isTrue();
    }

     /**
      * The canceled student leaves the schedule and goes into the cancellations
      * list — it does not disappear. That is the difference between the ADMIN
      * noticing the mistake and finding out through the student who showed up
      * at the studio.
      */
    @Test
    void canceledStudentMovesToTheCancellationList() throws Exception {
        String teacherName = unique("Professor Do Cancelado");
        long teacherId = createTeacher(teacherName);
        String studentName = unique("Aluna Cancelada Do Painel");
        long studentId = createStudent(studentName, teacherId);
        createDefaultSchedule(studentId, "10:00");
        long exceptionId = cancelClass(studentId, MONDAY);

        JsonNode day = daySchedule(MONDAY);

        assertThat(studentIdsOf(day.get("entries"))).doesNotContain(studentId);

        JsonNode cancellation = nodeFor(day.get("cancellations"), studentId);
        assertThat(cancellation.get("exceptionId").asLong()).isEqualTo(exceptionId);
        assertThat(cancellation.get("studentName").asText()).isEqualTo(studentName);
        assertThat(cancellation.get("teacherName").asText()).isEqualTo(teacherName);
        assertThat(cancellation.get("time").asText()).isEqualTo("10:00");
    }

     /**
      * A reschedule is not a cancellation: whoever was rescheduled appears —
      * with the undo available — on the destination day, where the class
      * actually happens.
      */
    @Test
    void rescheduledStudentIsNotListedAsCanceled() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Nao Cancelado"));
        long studentId = createStudent(unique("Aluno Apenas Remarcado"), teacherId);
        createDefaultSchedule(studentId, "11:00");
        rescheduleClass(studentId, MONDAY, WEDNESDAY, "09:00");

        assertThat(studentIdsOf(daySchedule(MONDAY).get("cancellations"))).doesNotContain(studentId);
    }

    @Test
    void weekendHasNoCancellations() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Sabado Cancelado"));
        long studentId = createStudent(unique("Aluno Do Sabado Cancelado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, SATURDAY);

        assertThat(daySchedule(SATURDAY).get("cancellations")).isEmpty();
    }

    private JsonNode entryOf(JsonNode day, long studentId) {
        return nodeFor(day.get("entries"), studentId);
    }

    /** The same student can have more than one class in a day: template + reschedule. */
    private JsonNode entryAt(JsonNode day, long studentId, String time) {
        for (JsonNode node : day.get("entries")) {
            if (node.get("studentId").asLong() == studentId && time.equals(node.get("time").asText())) {
                return node;
            }
        }
        throw new AssertionError("Aluno %d não tem aula às %s".formatted(studentId, time));
    }

    private JsonNode nodeFor(JsonNode list, long studentId) {
        for (JsonNode node : list) {
            if (node.get("studentId").asLong() == studentId) {
                return node;
            }
        }
        throw new AssertionError("Aluno %d não está na lista".formatted(studentId));
    }

    private java.util.List<Long> studentIdsOf(JsonNode list) {
        return list.findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
