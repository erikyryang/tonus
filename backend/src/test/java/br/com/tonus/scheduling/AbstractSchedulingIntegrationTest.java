package br.com.tonus.scheduling;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for the scheduling module's tests.
 *
 * Inherits from registration because the schedule does not exist without a student and a teacher: the seeding
 * continues to be done through the REST API, never through a repository — the repositories
 * live in {@code *.internal.domain} and the test runs in the module's root package.
 *
 * The dates are fixed literals and never derived from "today": the schedule expansion
 * depends on the day of the week, so a test that used the current date would pass
 * Monday through Friday and would break on Saturday.
 *
 * For the same reason the clock is fixed ({@link SchedulingClockConfig}): "today" is
 * always {@link #MONDAY}, and the dates below have a stable position relative to
 * it — past and future do not change with the passage of real time.
 */
@Import(SchedulingClockConfig.class)
abstract class AbstractSchedulingIntegrationTest extends AbstractRegistrationIntegrationTest {

    /** Monday — is also "today" for the tests' fixed clock. */
    protected static final String MONDAY = "2026-07-27";
    /** Tuesday. */
    protected static final String TUESDAY = "2026-07-28";
    /** Wednesday — destination of the reschedules. */
    protected static final String WEDNESDAY = "2026-07-29";
    /** Saturday. */
    protected static final String SATURDAY = "2026-08-01";
    /** Friday before "today" — to exercise the rejection of a past date. */
    protected static final String LAST_FRIDAY = "2026-07-24";

    protected long createDefaultSchedule(long studentId, String time) throws Exception {
        return idOf(postDefaultSchedule(studentId, time)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postDefaultSchedule(long studentId, String time) throws Exception {
        return mockMvc.perform(post("/default-schedules")
                .cookie(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":%d,"time":"%s"}
                        """.formatted(studentId, time)));
    }

    protected long createAnnouncement(String date, String description, boolean suspendsClasses) throws Exception {
        return idOf(postAnnouncement(date, description, suspendsClasses)
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postAnnouncement(String date, String description, boolean suspendsClasses)
            throws Exception {
        return mockMvc.perform(post("/announcements")
                .cookie(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"date":"%s","description":"%s","suspendsClasses":%b}
                        """.formatted(date, description, suspendsClasses)));
    }

    protected long cancelClass(long studentId, String date) throws Exception {
        return idOf(postException("""
                {"studentId":%d,"date":"%s","type":"CANCELED"}
                """.formatted(studentId, date))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected long rescheduleClass(long studentId, String date, String newDate, String newTime) throws Exception {
        return idOf(postException("""
                {"studentId":%d,"date":"%s","type":"RESCHEDULED","newDate":"%s","newTime":"%s"}
                """.formatted(studentId, date, newDate, newTime))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected ResultActions postException(String body) throws Exception {
        return mockMvc.perform(post("/schedule-exceptions")
                .cookie(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** The day's schedule, already expanded, as the ADMIN sees it. */
    protected JsonNode daySchedule(String date) throws Exception {
        MvcResult result = mockMvc.perform(get("/schedule/day").param("date", date).cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    /**
     * Cookie of a real student — with a link in {@code users.student_id}.
     * The {@code studentCookie} inherited from auth creates a user without a link, and it is
     * exactly the link that the /me endpoints use to find the student.
     */
    protected Cookie linkedStudentCookie(long studentId) throws Exception {
        return linkedCookie("STUDENT", "studentId", studentId);
    }

    protected Cookie linkedTeacherCookie(long teacherId) throws Exception {
        return linkedCookie("TEACHER", "teacherId", teacherId);
    }

    private Cookie linkedCookie(String role, String linkField, long linkId) throws Exception {
        String login = unique(linkField.toLowerCase()) + "@tonus.com";
        mockMvc.perform(post("/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s","role":"%s","%s":%d}
                                """.formatted(login, DEFAULT_PASSWORD, role, linkField, linkId)))
                .andExpect(status().isCreated());
        return extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
