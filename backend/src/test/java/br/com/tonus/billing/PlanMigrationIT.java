package br.com.tonus.billing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migration of subscriptions into plans (V020) — the riskiest part of
 * SPEC-012, and the only one that cannot be checked later: when the suite
 * runs, `subscription` has already been dropped by V022 — nothing left to
 * migrate.
 *
 * That is why the test recreates the source table in the V011 format, seeds
 * it, runs the migration's <strong>file</strong> and drops the copy at the
 * end. Running the file, not a transcription of the SQL, is what keeps the
 * test from going green while the real migration diverges.
 *
 * <p>The assertions look only at the plans that <em>this</em> run created,
 * and the watermark exists for a specific reason: V020 matches subscription
 * to plan <strong>by amount</strong>, and the Postgres is shared by the
 * suite — another class that created a R$ 350.00 plan makes the join match
 * two. In production this does not happen (V019 creates an empty `plan` and
 * V020 runs right after, in the same Flyway pass), but here it happens
 * per class order, and without the watermark the test passes or fails by
 * luck.
 */
class PlanMigrationIT extends AbstractBillingIntegrationTest {

    @AfterEach
    void dropSubscriptionCopy() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS subscription");
    }

    @Test
    void turnsEachDistinctAmountIntoOnePlanAndEnrollsItsStudents() throws Exception {
        recreateSubscriptionTable();
        long first = seedStudentOfMigration("Aluno De 350 Um");
        long second = seedStudentOfMigration("Aluno De 350 Dois");
        long third = seedStudentOfMigration("Aluno De 400");
        seedSubscription(first, "350.00", true);
        seedSubscription(second, "350.00", true);
        seedSubscription(third, "400.00", true);

        long before = lastPlanId();
        runMigration();

        Map<Long, Long> planByStudent = migratedPlanByStudent(List.of(first, second, third), before);
        // Same amount, same plan: the assumed consequence of using the amount
        // as identity (SPEC-012).
        assertThat(planByStudent.get(first)).isEqualTo(planByStudent.get(second));
        assertThat(planByStudent.get(third)).isNotEqualTo(planByStudent.get(first));

        assertThat(planNameOf(planByStudent.get(first))).isEqualTo("Plano R$ 350,00");
        assertThat(planNameOf(planByStudent.get(third))).isEqualTo("Plano R$ 400,00");
    }

    /** An inactive subscription becomes an inactive enrollment: the migration does not resurrect charges. */
    @Test
    void keepsTheActiveFlagOfTheSubscription() throws Exception {
        recreateSubscriptionTable();
        long studentId = seedStudentOfMigration("Aluno Da Assinatura Inativa");
        seedSubscription(studentId, "199.00", false);

        long before = lastPlanId();
        runMigration();

        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM plan_enrollment WHERE student_id = ? AND plan_id > ?",
                Boolean.class, studentId, before);
        assertThat(active).isFalse();
    }

    /**
     * The plan name cannot depend on the database configuration: with to_char
     * using G/D, the separator would come from the server's lc_numeric and
     * the same amount would become "Plano R$ 1.500,00" or "Plano R$ 1,500.00"
     * depending on the machine.
     */
    @Test
    void formatsThousandsWithoutDependingOnTheDatabaseLocale() throws Exception {
        recreateSubscriptionTable();
        long studentId = seedStudentOfMigration("Aluno Do Plano Caro");
        seedSubscription(studentId, "1500.00", true);

        long before = lastPlanId();
        runMigration();

        assertThat(planNameOf(migratedPlanByStudent(List.of(studentId), before).get(studentId)))
                .isEqualTo("Plano R$ 1500,00");
    }

    /** The V011 format, so the migration has something to read from. */
    private void recreateSubscriptionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE subscription (
                    id BIGSERIAL PRIMARY KEY,
                    student_id BIGINT NOT NULL,
                    monthly_amount NUMERIC(10, 2) NOT NULL,
                    due_day INT NOT NULL,
                    active BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
    }

    private long seedStudentOfMigration(String name) throws Exception {
        return createStudent(unique(name), createTeacher(unique("Professor De " + name)));
    }

    private void seedSubscription(long studentId, String monthlyAmount, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO subscription (student_id, monthly_amount, due_day, active, created_at, updated_at)
                VALUES (?, ?::numeric, 10, ?, now(), now())
                """, studentId, monthlyAmount, active);
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V020__billing_migrate_subscriptions_to_plans.sql")
                        .getInputStream(),
                StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    /** The id of the last plan that existed before the migration: the watermark. */
    private long lastPlanId() {
        Long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM plan", Long.class);
        return id == null ? 0 : id;
    }

    private Map<Long, Long> migratedPlanByStudent(List<Long> studentIds, long lastPlanIdBefore) {
        return jdbcTemplate.query("""
                        SELECT student_id, plan_id FROM plan_enrollment
                        WHERE student_id = ANY (?) AND plan_id > ?
                        """,
                        ps -> {
                            ps.setArray(1, ps.getConnection().createArrayOf("bigint", studentIds.toArray()));
                            ps.setLong(2, lastPlanIdBefore);
                        },
                        rs -> {
                            Map<Long, Long> byStudent = new java.util.HashMap<>();
                            while (rs.next()) {
                                byStudent.put(rs.getLong("student_id"), rs.getLong("plan_id"));
                            }
                            return byStudent;
                        });
    }

    private String planNameOf(Long planId) {
        return jdbcTemplate.queryForObject("SELECT name FROM plan WHERE id = ?", String.class, planId);
    }
}
