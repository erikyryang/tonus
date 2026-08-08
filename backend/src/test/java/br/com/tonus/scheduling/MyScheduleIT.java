package br.com.tonus.scheduling;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion: GET /me/schedule of one student never returns another
 * student's data.
 *
 * The student id comes from the token and there is no way to provide it — not
 * path, not query, not body. That is the design that prevents IDOR: it is not
 * enough for the endpoint to check whether the requested id is the user's,
 * because that check is exactly what gets forgotten. Here there is nothing to
 * check.
 */
class MyScheduleIT extends AbstractSchedulingIntegrationTest {

     /**
      * Wednesday unused by any other test. An announcement is global and the
      * database is shared across the suite: registering one on MONDAY/TUESDAY
      * would wipe that day's schedule for all other classes.
      */
    private static final String OWN_HOLIDAY = "2026-08-26";

     /**
      * A window exclusive to this class, away from the dates the others use.
      *
      * An announcement is global and the database is shared across the suite:
      * suspending a day inside MONDAY..WEDNESDAY would wipe that day for
      * everyone, and WEDNESDAY is precisely the destination of the other tests'
      * reschedules.
      */
    private static final String WEEK_START = "2026-09-07";
    private static final String WEEK_SECOND = "2026-09-08";
    private static final String WEEK_HOLIDAY = "2026-09-09";

    @Test
    void studentSeesOnlyOwnClasses() throws Exception {
        long teacherId = createTeacher(unique("Professora Do Me"));
        long mine = createStudent(unique("Aluno Dono Do Token"), teacherId);
        long other = createStudent(unique("Aluno Alheio"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");

        List<Long> visible = studentIdsOf(mySchedule(linkedStudentCookie(mine), MONDAY));

        assertThat(visible).containsOnly(mine);
    }

     /**
      * Sending another student's id in the query changes nothing: the parameter
      * does not exist in the contract, so it is ignored — it never becomes the
      * id being queried.
      */
    @Test
    void studentIdInQueryIsIgnored() throws Exception {
        long teacherId = createTeacher(unique("Professor Do IDOR"));
        long mine = createStudent(unique("Aluno Que Tenta"), teacherId);
        long victim = createStudent(unique("Aluno Vitima"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(victim, "08:00");

        MvcResult result = mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .param("studentId", String.valueOf(victim))
                        .cookie(linkedStudentCookie(mine)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(studentIdsOf(json(result))).containsOnly(mine);
    }

    @Test
    void cancellationRemovesTheClassFromMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Cancelado"));
        long studentId = createStudent(unique("Aluno Do Me Cancelado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        assertThat(studentIdsOf(mySchedule(student, MONDAY))).containsOnly(studentId);

        cancelClass(studentId, MONDAY);

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
    }

    @Test
    void rescheduleMovesTheClassInMySchedule() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Me Remarcado"));
        long studentId = createStudent(unique("Aluno Do Me Remarcado"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        Cookie student = linkedStudentCookie(studentId);

        rescheduleClass(studentId, MONDAY, WEDNESDAY, "08:00");

        assertThat(mySchedule(student, MONDAY).get("entries")).isEmpty();
        assertThat(studentIdsOf(mySchedule(student, WEDNESDAY))).containsOnly(studentId);
    }

     /**
      * The student needs to know why the day is empty (SPEC-009). Without the
      * announcement's description, their screen would have to guess "it's
      * empty, must be a day without class" — inventing a reason there is no way
      * to verify.
      */
    @Test
    void suspendedDayExplainsTheEmptyDay() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Feriado No Me"));
        long studentId = createStudent(unique("Aluno Do Feriado No Me"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        createAnnouncement(OWN_HOLIDAY, "Feriado do Me", true);

        JsonNode day = mySchedule(linkedStudentCookie(studentId), OWN_HOLIDAY);

        assertThat(day.get("entries")).isEmpty();
        assertThat(day.get("announcement").get("description").asText()).isEqualTo("Feriado do Me");
    }

     /**
      * The canceled class disappears from the schedule by definition — and that
      * is precisely why the student needs to see it separately: without the
      * cancellation in the response, "no class today" and "your class today was
      * canceled" look identical.
      */
    @Test
    void ownCancellationIsVisible() throws Exception {
        long teacherId = createTeacher(unique("Professor Do Cancelamento No Me"));
        long mine = createStudent(unique("Aluno Que Ve O Cancelamento"), teacherId);
        long other = createStudent(unique("Aluno Alheio Cancelado"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");
        cancelClass(mine, MONDAY);
        cancelClass(other, MONDAY);

        JsonNode day = mySchedule(linkedStudentCookie(mine), MONDAY);

        assertThat(day.get("cancellations")).hasSize(1);
        assertThat(day.get("cancellations").get(0).get("studentId").asLong()).isEqualTo(mine);
    }

     /**
      * The ADMIN has no linked student in the token. Calling a /me endpoint
      * without a link is a usage error, not an empty schedule — empty would
      * hide the bug.
      */
    @Test
    void adminHasNoOwnSchedule() throws Exception {
        mockMvc.perform(get("/me/schedule").param("date", MONDAY).cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotUseStudentEndpoint() throws Exception {
        long teacherId = createTeacher(unique("Professor Curioso"));

        mockMvc.perform(get("/me/schedule")
                        .param("date", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

     /**
      * The whole window in a single call.
      *
      * The student's screen shows the upcoming days, and asking day by day was
      * seven requests — each one expanding the whole studio's schedule to
      * return a single student's row. The contract is the same as the
      * standalone day, repeated per date: the client rebuilds nothing.
      */
    @Test
    void weekReturnsOneDayPerDateInTheWindow() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Semana"));
        long studentId = createStudent(unique("Aluno Da Semana"), teacherId);
        createDefaultSchedule(studentId, "07:00");

        JsonNode week = myWeek(linkedStudentCookie(studentId), MONDAY, 3);

        assertThat(week).hasSize(3);
        assertThat(week.findValues("date").stream().map(JsonNode::asText).toList())
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        // Monday, Tuesday and Wednesday are weekdays: the 07:00 class appears on all three.
        assertThat(week.findValues("entries").stream().allMatch(entries -> entries.size() == 1)).isTrue();
    }

    /** The student's window sees the same slice as the standalone day: only them. */
    @Test
    void weekNeverLeaksAnotherStudent() throws Exception {
        long teacherId = createTeacher(unique("Professora Da Semana Alheia"));
        long mine = createStudent(unique("Aluno Dono Da Semana"), teacherId);
        long other = createStudent(unique("Aluno Alheio Da Semana"), teacherId);
        createDefaultSchedule(mine, "07:00");
        createDefaultSchedule(other, "08:00");

        JsonNode week = myWeek(linkedStudentCookie(mine), MONDAY, 2);

        assertThat(week.findValues("studentId").stream().map(JsonNode::asLong).distinct().toList())
                .containsOnly(mine);
    }

    /** Announcement and cancellation explain the empty day in the window too. */
    @Test
    void weekCarriesAnnouncementAndCancellation() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Semana Cheia"));
        long studentId = createStudent(unique("Aluno Da Semana Cheia"), teacherId);
        createDefaultSchedule(studentId, "07:00");
        cancelClass(studentId, WEEK_SECOND);
        createAnnouncement(WEEK_HOLIDAY, "Feriado da Semana", true);

        JsonNode week = myWeek(linkedStudentCookie(studentId), WEEK_START, 3);

        assertThat(week.get(1).get("cancellations")).hasSize(1);
        assertThat(week.get(2).get("announcement").get("description").asText()).isEqualTo("Feriado da Semana");
        assertThat(week.get(2).get("entries")).isEmpty();
    }

    @Test
    void teacherCannotUseTheStudentWeek() throws Exception {
        long teacherId = createTeacher(unique("Professor Curioso Da Semana"));

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .cookie(linkedTeacherCookie(teacherId)))
                .andExpect(status().isForbidden());
    }

    /** A window without a cap would be an expansion without a cap: the cost is per requested day. */
    @Test
    void weekRefusesAWindowLargerThanTheMaximum() throws Exception {
        long teacherId = createTeacher(unique("Professor Da Janela Grande"));
        long studentId = createStudent(unique("Aluno Da Janela Grande"), teacherId);

        mockMvc.perform(get("/me/schedule/week")
                        .param("from", MONDAY)
                        .param("days", "32")
                        .cookie(linkedStudentCookie(studentId)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode mySchedule(Cookie student, String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/schedule").param("date", date).cookie(student))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode myWeek(Cookie student, String from, int days) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/schedule/week")
                        .param("from", from)
                        .param("days", String.valueOf(days))
                        .cookie(student))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private List<Long> studentIdsOf(JsonNode day) {
        return day.get("entries").findValues("studentId").stream().map(JsonNode::asLong).toList();
    }
}
