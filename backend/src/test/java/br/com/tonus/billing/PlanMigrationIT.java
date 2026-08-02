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
 * A migração das assinaturas para planos (V020) — a parte mais arriscada da
 * SPEC-012, e a única que não tem como ser conferida depois: quando a suíte
 * roda, `subscription` já foi derrubada pela V022 e não há mais o que migrar.
 *
 * Por isso o teste recria a tabela de origem no formato da V011, semeia,
 * executa o <strong>arquivo</strong> da migration e derruba a cópia no fim.
 * Executar o arquivo, e não uma transcrição do SQL, é o que impede o teste de
 * ficar verde enquanto a migration de verdade diverge.
 *
 * <p>As asserções olham só para os planos que <em>esta</em> execução criou, e a
 * marca d'água existe por um motivo específico: a V020 casa assinatura com
 * plano <strong>pelo valor</strong>, e o Postgres é compartilhado pela suíte —
 * outra classe que tenha criado um plano de R$ 350,00 faz o join casar com
 * dois. Em produção isso não acontece (a V019 cria `plan` vazia e a V020 roda
 * em seguida, no mesmo passe do Flyway), mas aqui acontece conforme a ordem das
 * classes, e sem a marca d'água o teste passa ou falha por sorteio.
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
        // Mesmo valor, mesmo plano: é a consequência assumida de usar o valor
        // como identidade (SPEC-012).
        assertThat(planByStudent.get(first)).isEqualTo(planByStudent.get(second));
        assertThat(planByStudent.get(third)).isNotEqualTo(planByStudent.get(first));

        assertThat(planNameOf(planByStudent.get(first))).isEqualTo("Plano R$ 350,00");
        assertThat(planNameOf(planByStudent.get(third))).isEqualTo("Plano R$ 400,00");
    }

    /** Assinatura inativa vira matrícula inativa: a migração não ressuscita cobrança. */
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
     * O nome do plano não pode depender da configuração do banco: com to_char
     * usando G/D, o separador sairia do lc_numeric do servidor e o mesmo valor
     * viraria "Plano R$ 1.500,00" ou "Plano R$ 1,500.00" conforme a máquina.
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

    /** O formato da V011, para a migration ter de onde ler. */
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

    /** O id do último plano existente antes da migration: a marca d'água. */
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
