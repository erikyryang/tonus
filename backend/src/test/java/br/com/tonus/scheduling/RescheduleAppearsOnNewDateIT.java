package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance criterion: the reschedule disappears from the original date and
 * appears on the new date.
 *
 * Both halves matter. Only disappearing is a cancellation; only appearing
 * duplicates the student in the week. This is the case that proves the
 * expansion adds and subtracts from the same source, instead of treating an
 * exception as an "extra" tacked on at the end.
 */
class RescheduleAppearsOnNewDateIT extends AbstractSchedulingIntegrationTest {

    @Test
    void rescheduledClassMovesToTheNewDate() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Remarcacao"));
        long studentId = createStudent(unique("Aluna Remarcada"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(studentIdsOf(daySchedule(MONDAY))).doesNotContain(studentId);

        JsonNode moved = entryOf(daySchedule(WEDNESDAY), studentId, "RESCHEDULED");
        assertThat(moved.get("time").asText()).isEqualTo("08:00");
        assertThat(moved.get("origin").asText()).isEqualTo("RESCHEDULED");
    }

     /**
      * The reschedule carries the new time, not the template's. If it fell
      * back to the default time, the student would appear at the wrong time of
      * the right day.
      */
    @Test
    void rescheduledClassKeepsItsOwnTimeNotTheTemplateOne() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Hora Nova"));
        long studentId = createStudent(unique("Aluno Da Hora Nova"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, TUESDAY, WEDNESDAY, "19:00");

        assertThat(entryOf(daySchedule(WEDNESDAY), studentId, "RESCHEDULED").get("time").asText())
                .isEqualTo("19:00");
    }

     /**
      * A student rescheduled to a day where they already have a class appears
      * twice that day — the template one and the rescheduled one. They are two
      * real classes.
      */
    @Test
    void rescheduleIntoADayWithTemplateShowsBothClasses() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Aula Dobrada"));
        long studentId = createStudent(unique("Aluno Com Duas Aulas"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, TUESDAY, "20:00");

        List<Long> tuesday = studentIdsOf(daySchedule(TUESDAY));
        assertThat(tuesday).filteredOn(id -> id == studentId).hasSize(2);
    }

     /**
      * Rescheduling to a Saturday works: the weekend has no template, but
      * nothing prevents a one-off make-up class there.
      */
    @Test
    void rescheduleToWeekendIsVisible() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Reposicao"));
        long studentId = createStudent(unique("Aluno Da Reposicao"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        rescheduleClass(studentId, MONDAY, SATURDAY, "10:00");

        assertThat(entryOf(daySchedule(SATURDAY), studentId, "RESCHEDULED").get("time").asText())
                .isEqualTo("10:00");
    }

     /**
      * The origin is part of the lookup, and it is not a detail: a student
      * rescheduled to a day where they already have a class appears twice that
      * day. Searching only by id would find the template class — which comes
      * first in the time ordering — and the test would end up asserting
      * something about the wrong class.
      */
    private JsonNode entryOf(JsonNode day, long studentId, String origin) {
        for (JsonNode entry : day.get("entries")) {
            if (entry.get("studentId").asLong() == studentId && entry.get("origin").asText().equals(origin)) {
                return entry;
            }
        }
        throw new AssertionError("Aluno %d não tem aula %s em %s"
                .formatted(studentId, origin, day.get("date").asText()));
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
