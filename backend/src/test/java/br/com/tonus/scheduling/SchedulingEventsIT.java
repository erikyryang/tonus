package br.com.tonus.scheduling;

import br.com.tonus.scheduling.internal.SchedulingService;
import br.com.tonus.shared.TestClockConfig;
import br.com.tonus.support.PostgresContainer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What scheduling promises to the rest of the system (docs/eventos.md):
 * canceling, rescheduling and registering an announcement publish an event.
 * That is the contract the notification will depend on — scheduling cannot
 * call WhatsApp, only notify that something happened.
 *
 * It runs as {@code @ApplicationModuleTest} on purpose: it starts scheduling
 * and its dependencies, and fails if the module needs someone it did not
 * declare. A whole {@code @SpringBootTest} would hide exactly that error.
 *
 * The seed goes through SQL because there is no HTTP layer here: the
 * registration facade is read-only, by architecture decision, so there is no
 * way to create a student through it.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
class SchedulingEventsIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

     /**
      * The Clock comes in through a nested configuration, not through
      * {@code @Import(TestClockConfig.class)}: the module bootstrap filters the
      * bean definitions by the packages of the module under test, and
      * TestClockConfig lives in shared — it would be discarded. Declared here
      * inside, it survives.
      *
      * The bean is necessary for the context to even start: the shared
      * AuditListener is an entity listener for every BaseEntity and receives
      * Clock in its constructor, so without it the entityManagerFactory is not
      * created.
      *
      * {@code @Primary} is not decoration: without it the production Clock
      * stays in the context and wins the injection by parameter-name
      * coincidence ({@code clock}), leaving the test stuck to the real
      * calendar — that is how {@code reschedulePublishesClassRescheduled}
      * started failing on its own when 2026-07-29 became the past.
      */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestClockConfig.FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");
    private static final LocalDate WEDNESDAY = LocalDate.parse("2026-07-29");

    @Autowired
    private SchedulingService schedulingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cancellationPublishesClassCanceled(AssertablePublishedEvents events) {
        long studentId = seedStudentWithSchedule();

        schedulingService.cancelClass(studentId, MONDAY);

        assertThat(events)
                .contains(ClassCanceled.class)
                .matchingValue(ClassCanceled::studentId, studentId)
                .matchingValue(ClassCanceled::date, MONDAY);
    }

    @Test
    void reschedulePublishesClassRescheduled(AssertablePublishedEvents events) {
        long studentId = seedStudentWithSchedule();

        schedulingService.rescheduleClass(studentId, MONDAY, WEDNESDAY, LocalTime.of(8, 0));

        assertThat(events)
                .contains(ClassRescheduled.class)
                .matchingValue(ClassRescheduled::studentId, studentId)
                .matchingValue(ClassRescheduled::originalDate, MONDAY)
                .matchingValue(ClassRescheduled::newDate, WEDNESDAY)
                .matchingValue(ClassRescheduled::newTime, LocalTime.of(8, 0));
    }

    /**
     * No class on the origin day means there is nobody to tell: the "canceled"
     * notice exists to inform a student who would have come. The exception row
     * is still created — it is what the panel shows — but the event, whose
     * only consumer is the WhatsApp notice, does not go out.
     */
    @Test
    void cancelingADayWithoutAClassPublishesNoEvent(AssertablePublishedEvents events) {
        long studentId = seedStudent();

        schedulingService.cancelClass(studentId, MONDAY);

        assertThat(events.ofType(ClassCanceled.class)).isEmpty();
    }

    @Test
    void announcementPublishesAnnouncementCreated(AssertablePublishedEvents events) {
        LocalDate date = LocalDate.parse("2026-12-25");

        long id = schedulingService.createAnnouncement(date, "Natal", true).getId();

        assertThat(events)
                .contains(AnnouncementCreated.class)
                .matchingValue(AnnouncementCreated::announcementId, id)
                .matchingValue(AnnouncementCreated::date, date)
                .matchingValue(AnnouncementCreated::description, "Natal")
                .matchingValue(AnnouncementCreated::suspendsClasses, true);
    }

     /**
      * Student and teacher come in through SQL: the tables belong to
      * registration, and its facade creates nothing — which is the right
      * decision, not a limitation to work around by changing the contract just
      * for the test.
      */
    private long seedStudent() {
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES ('Professor Do Evento', '+5511999990000', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES ('Aluno Do Evento', '+5511988880000', ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, teacherId);
    }

    /** A student who actually has a class to cancel or reschedule. */
    private long seedStudentWithSchedule() {
        long studentId = seedStudent();
        schedulingService.createDefaultSchedule(studentId, LocalTime.of(7, 0));
        return studentId;
    }
}
