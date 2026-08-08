package br.com.tonus.billing;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.billing.internal.domain.InvoiceRepository;
import br.com.tonus.support.PostgresContainer;
import java.time.Clock;
import java.time.Instant;
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
 * The daily job is billing's backbone (SPEC-004): generates today's invoice
 * and marks an overdue invoice as such. Runs as {@code @ApplicationModuleTest}
 * on purpose, same pattern as {@code SchedulingEventsIT} — brings up billing
 * and its dependencies, and fails if the module needs someone not declared.
 *
 * The tests call {@link BillingService} directly, not
 * {@code BillingJob.runDailyJob()}: {@code @SchedulerLock} uses the real
 * clock (not the injected Clock) and a fixed key ("billing-daily-job"), so
 * two calls close together in real time — between test methods, not just
 * within one — collide with {@code lockAtLeastFor} and the second silently
 * doesn't run. That would test ShedLock's lock, not the spec's business
 * idempotency ("job running twice doesn't duplicate"), which is the
 * existence pre-check + uk_invoice_student_period.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
class BillingJobIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /**
     * Nested configuration, not {@code @Import}: the module's bootstrap
     * filters bean definitions by the packages of the module under test,
     * same reason documented in {@code SchedulingEventsIT.FixedClockConfig}.
     */
    @TestConfiguration
    static class FixedClockConfig {

        static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BillingService billingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void jobRunningTwiceDoesNotDuplicateInvoiceOrEvent(AssertablePublishedEvents events) {
        // Registered on the 15th: that's their due day, and it's "today"
        // for this class's fixed clock — without it the job would have
        // nothing to bill.
        long studentId = seedStudent("2026-01-15 10:00:00");
        seedEnrollment(studentId, seedPlan("Plano Do Job", "220.00"));

        billingService.generateDueInvoices();
        billingService.generateDueInvoices();

        assertThat(invoiceRepository.findAll())
                .filteredOn(invoice -> invoice.getStudentId().equals(studentId))
                .hasSize(1);
        // Filters by student: Postgres is shared across billing's test
        // classes with no rollback, so other invoices/events from the job's
        // global sweep can coexist in the same run.
        assertThat(events.ofType(InvoiceDueSoon.class).matchingValue(InvoiceDueSoon::studentId, studentId))
                .hasSize(1);
    }

    @Test
    void jobTransitionsOverdueInvoiceOnceAndPublishesOnce(AssertablePublishedEvents events) {
        long invoiceId = seedInvoice(seedStudent("2026-01-01 10:00:00"),
                "2026-07-01", "180.00", "2026-07-05", "OPEN");

        billingService.markOverdueInvoices();
        billingService.markOverdueInvoices();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM invoice WHERE id = ?", String.class, invoiceId);
        assertThat(status).isEqualTo("OVERDUE");
        // Filters by the invoice: markOverdueInvoices scans the whole
        // table, and Postgres is shared across billing's test classes with
        // no rollback — other overdue OPEN invoices can coexist and also
        // become OVERDUE in this same call.
        assertThat(events.ofType(InvoiceOverdue.class).matchingValue(InvoiceOverdue::invoiceId, invoiceId))
                .hasSize(1);
    }

    private long seedStudent(String createdAt) {
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES ('Professor Do Job', '+5511999990000', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES ('Aluno Do Job', '+5511988880000', ?, TRUE, ?::timestamp, now())
                RETURNING id
                """, Long.class, teacherId, createdAt);
    }

    private long seedPlan(String name, String monthlyPrice) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO plan (name, monthly_price, active, created_at, updated_at)
                VALUES (?, ?::numeric, TRUE, now(), now())
                RETURNING id
                """, Long.class, name, monthlyPrice);
    }

    private void seedEnrollment(long studentId, long planId) {
        jdbcTemplate.update("""
                INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at)
                VALUES (?, ?, TRUE, now(), now())
                """, studentId, planId);
    }

    private long seedInvoice(long studentId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, studentId, referenceMonth, amount, dueDate, status);
    }
}
