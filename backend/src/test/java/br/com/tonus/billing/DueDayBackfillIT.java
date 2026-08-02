package br.com.tonus.billing;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A migration que realinha o vencimento das assinaturas que já existiam quando
 * a regra mudou (V016).
 *
 * O teste executa o <strong>arquivo</strong> da migration, não uma cópia do SQL:
 * uma cópia passaria verde enquanto o arquivo de verdade diverge, e é o arquivo
 * que roda em produção. Rodar de novo é seguro — o UPDATE recalcula a partir da
 * origem e ignora quem já está certo.
 */
class DueDayBackfillIT extends AbstractBillingIntegrationTest {

    @Test
    void realignsDueDayOfSubscriptionsCreatedUnderTheOldRule() throws Exception {
        long studentId = createStudent(unique("Aluno Da Regra Antiga"), createTeacher(unique("Professor Da Regra Antiga")));
        long subscriptionId = createSubscription(studentId, "200.00");
        registeredAt(studentId, "2026-03-05 12:00:00");
        // O que a regra antiga teria deixado: o dia da última baixa.
        jdbcTemplate.update("UPDATE subscription SET due_day = 22 WHERE id = ?", subscriptionId);

        runMigration();

        assertThat(dueDayOf(subscriptionId)).isEqualTo(5);
    }

    /**
     * O dia é o do fuso do estúdio, não o de UTC. Quem se cadastrou às 21h30 de
     * Brasília se cadastrou no dia 5; em UTC já era dia 6, e a mensalidade
     * venceria um dia depois para todo aluno cadastrado no fim da noite.
     */
    @Test
    void usesTheStudioTimeZoneToDecideTheDay() throws Exception {
        long studentId = createStudent(unique("Aluno Da Meia Noite"), createTeacher(unique("Professor Da Meia Noite")));
        long subscriptionId = createSubscription(studentId, "200.00");
        // 00:30 UTC do dia 6 = 21h30 do dia 5 em São Paulo.
        registeredAt(studentId, "2026-03-06 00:30:00");
        jdbcTemplate.update("UPDATE subscription SET due_day = 22 WHERE id = ?", subscriptionId);

        runMigration();

        assertThat(dueDayOf(subscriptionId)).isEqualTo(5);
    }

    /** Quem já está no dia certo não é tocado — nem o due_day, nem o updated_at. */
    @Test
    void leavesAlreadyCorrectSubscriptionsUntouched() throws Exception {
        long studentId = createStudent(unique("Aluno Ja Certo"), createTeacher(unique("Professor Ja Certo")));
        long subscriptionId = createSubscription(studentId, "200.00");
        int dueDayBefore = dueDayOf(subscriptionId);
        String updatedAtBefore = updatedAtOf(subscriptionId);

        runMigration();

        assertThat(dueDayOf(subscriptionId)).isEqualTo(dueDayBefore);
        assertThat(updatedAtOf(subscriptionId)).isEqualTo(updatedAtBefore);
    }

    /**
     * O created_at é gravado pelo JPA a partir do Clock e não é editável pela
     * API ({@code updatable = false}); reescrevê-lo por SQL é a única forma de
     * ter um aluno "cadastrado" em outro dia.
     */
    private void registeredAt(long studentId, String utcTimestamp) {
        jdbcTemplate.update("UPDATE student SET created_at = ?::timestamp WHERE id = ?", utcTimestamp, studentId);
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V016__billing_realign_due_day_to_registration.sql")
                        .getInputStream(),
                StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private int dueDayOf(long subscriptionId) {
        Integer dueDay = jdbcTemplate.queryForObject(
                "SELECT due_day FROM subscription WHERE id = ?", Integer.class, subscriptionId);
        return dueDay == null ? -1 : dueDay;
    }

    private String updatedAtOf(long subscriptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at::text FROM subscription WHERE id = ?", String.class, subscriptionId);
    }
}
