package br.com.tonus.notification;

import br.com.tonus.notification.support.NotificationTestConfig;
import br.com.tonus.notification.support.RecordingWhatsAppSender;
import br.com.tonus.support.PostgresContainer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base of the notification module tests.
 *
 * Deliberately runs as {@code @ApplicationModuleTest}: it boots notification and
 * the dependencies it declares, and fails if the module needs someone it did
 * not declare. A full {@code @SpringBootTest} would hide exactly that error.
 *
 * Seeding goes through SQL because the registration facade is read-only, by
 * architecture decision — there is no way to create a student through it.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
@Import(NotificationTestConfig.class)
abstract class AbstractNotificationIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /**
     * Postgres is shared between test classes and there is no rollback between
     * them. A unique phone per student is what lets each assertion look only at
     * its own — without it, a student created by another class would be counted.
     */
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected RecordingWhatsAppSender sender;

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void resetSender() {
        sender.reset();
    }

    /**
     * Publishes inside a transaction because {@code @ApplicationModuleListener}
     * is a {@code @TransactionalEventListener} on AFTER_COMMIT: without a
     * committing transaction, the event is discarded and the listener never
     * runs. This is also what creates the row in event_publication, without
     * which there is no reprocessing.
     */
    protected void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    protected Student seedStudent(String name) {
        String phone = "+55119%08d".formatted(PHONE_SEQUENCE.incrementAndGet());
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES (?, ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, "Professor " + name, phone.replace("+55119", "+55118"));
        Long studentId = jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, name, phone, teacherId);
        return new Student(studentId, name, phone);
    }

    protected long countLogs(long studentId, String template) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification_log
                WHERE student_id = ? AND template = ?
                """, Long.class, studentId, template);
    }

    /**
     * Both counts below are trimmed by the student inside the serialized
     * payload. Postgres is shared and other classes publish the same event
     * types: counting by type alone would mix tests.
     */
    protected long countCompletedPublications(String eventTypeSuffix, long studentId) {
        return countPublications(eventTypeSuffix, studentId, "completion_date IS NOT NULL");
    }

    protected long countIncompletePublications(String eventTypeSuffix, long studentId) {
        return countPublications(eventTypeSuffix, studentId, "completion_date IS NULL");
    }

    /**
     * The per-student trim uses regex, not LIKE: {@code '%"studentId":15%'}
     * also matches 150 and 151. The ids are sequential and one class seeds
     * several students, so the counter would occasionally inflate and a wait
     * would finish early — a failure that only shows up in certain id ranges.
     */
    private long countPublications(String eventTypeSuffix, long studentId, String completionClause) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE ?
                  AND serialized_event ~ ?
                  AND %s
                """.formatted(completionClause),
                Long.class, "%" + eventTypeSuffix, "\"studentId\":" + studentId + "(\\D|$)");
    }

    /**
     * Deactivates whoever is active and returns the ids, so the caller can
     * restore them.
     *
     * Only the fan-out needs this — it asks registration who is active and the
     * answer is the whole database, including students from other classes. The
     * restoration is mandatory: Postgres is shared with no rollback between
     * classes, and leaving the whole studio deactivated is the kind of gotcha
     * that breaks a distant test with no apparent connection.
     */
    protected List<Long> deactivateExistingStudents() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM student WHERE active = TRUE", Long.class);
        jdbcTemplate.update("UPDATE student SET active = FALSE WHERE active = TRUE");
        return ids;
    }

    protected void reactivate(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return;
        }
        // IN with placeholders, not ANY(?): JdbcTemplate treats the array as
        // positional parameters, not as a SQL array.
        String placeholders = String.join(",", Collections.nCopies(studentIds.size(), "?"));
        jdbcTemplate.update("UPDATE student SET active = TRUE WHERE id IN (%s)".formatted(placeholders),
                studentIds.toArray());
    }

    protected record Student(Long id, String name, String phone) {
    }
}
