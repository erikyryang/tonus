package br.com.tonus.billing;

import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two paths in SPEC-012 that promise to work in bulk: the plan listing
 * (which counts enrollments per plan) and the daily job (which resolves
 * price and registration date for the whole studio).
 *
 * Without these tests the two promises are just a comment: going back to
 * N+1 breaks no behavior assertion, and the cost shows up in production, in
 * the job that runs overnight over everyone.
 *
 * The assertions are ceilings, not exact counts — the exact number depends
 * on Hibernate details that change without the design changing. The ceiling
 * separates "bulk" from "per row", which is what matters.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class BillingQueryCountIT extends AbstractBillingIntegrationTest {

    private static final int PLANS = 6;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private br.com.tonus.billing.internal.BillingService billingService;

    /**
     * The enrollment count comes from one query for the whole page, not one
     * per plan — that's how it was written first, and the ceiling below is
     * what prevents going back.
     */
    @Test
    void planListingDoesNotCountPerPlan() throws Exception {
        String marker = unique("Contagem");
        for (int i = 0; i < PLANS; i++) {
            long planId = createPlan(marker + "-plano-" + i, "100.00");
            enroll(createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i)), planId);
        }

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        mockMvc.perform(get("/plans").param("size", String.valueOf(PLANS)).cookie(admin))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount())
                .as("consultas para montar uma página de %d planos", PLANS)
                .isLessThanOrEqualTo(5);
    }

    /**
     * The job resolves plans, enrollments, discount and registration dates
     * **as sets**, before deciding who to bill. Asking the registration
     * date student by student would be N extra queries in the only routine
     * that works over the whole studio overnight.
     *
     * These students' due dates fall on another day on purpose: this way
     * the job does all the lookups and generates nothing, and the count
     * isolates what we want to guarantee — the read path — from the cost
     * of inserting an invoice, which is proportional by nature (insert +
     * audit + event recording) and isn't N+1.
     *
     * The due date must be *ahead* of today for the job to skip them: the
     * job invoices whoever's due date has already passed (catch-up), so a
     * past due date would generate an invoice and the ceiling would measure
     * the write path.
     */
    @Test
    void dailyJobResolvesEverythingInBulkBeforeDeciding() throws Exception {
        // The job scans the whole studio and Postgres is shared by the
        // suite: an enrollment left by another class would enter the count
        // and the ceiling would measure the rest of the suite, not this
        // design. Same trick as AnnouncementFanOutIT — silence the rest and
        // restore it at the end.
        List<Long> previouslyActive = deactivateExistingEnrollments();
        try {
            String marker = unique("Job");
            long planId = createPlan(marker + "-plano", "100.00");
            for (int i = 0; i < PLANS; i++) {
                long studentId = createStudent(marker + "-aluno-" + i, createTeacher(marker + "-prof-" + i));
                enroll(studentId, planId);
                // Registered on the 30th; their July due date (the 30th) is
                // still ahead of "today" for the suite's clock (the 27th).
                jdbcTemplate.update("UPDATE student SET created_at = ?::timestamp WHERE id = ?",
                        "2026-03-30 09:00:00", studentId);
            }

            Statistics statistics = sessionFactory.getStatistics();
            statistics.clear();

            billingService.generateDueInvoices();

            // Plans, enrollments, configuration and registration dates:
            // four queries, and the number doesn't scale with the studio's size.
            assertThat(statistics.getPrepareStatementCount())
                    .as("consultas de leitura do job para %d alunos com plano ativo", PLANS)
                    .isLessThanOrEqualTo(5);
        } finally {
            reactivate(previouslyActive);
        }
    }

    private List<Long> deactivateExistingEnrollments() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM plan_enrollment WHERE active = TRUE", Long.class);
        jdbcTemplate.update("UPDATE plan_enrollment SET active = FALSE WHERE active = TRUE");
        return ids;
    }

    private void reactivate(List<Long> ids) {
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE plan_enrollment SET active = TRUE WHERE id = ?", id);
        }
    }
}
