package br.com.tonus.notification;

import br.com.tonus.notification.internal.NotificationLogCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A poda apaga o histórico velho e preserva o recente.
 *
 * O recorte é por {@code sent_at}, e não pela idade da linha no banco: é a data
 * do aviso que diz se a publicação correspondente ainda pode ser reentregue, e
 * é essa reentrega que a linha existe para barrar.
 */
class NotificationLogCleanupIT extends AbstractNotificationIntegrationTest {

    @Autowired
    private NotificationLogCleanupJob cleanupJob;

    @Test
    void deletesNoticesBeyondRetentionAndKeepsTheRest() {
        Student student = seedStudent("Aluno Do Historico");
        // O relógio do teste é fixo; as datas são relativas a ele, e a retenção
        // padrão é de 180 dias.
        insertNotice(student.id(), "CLASS_CANCELED", "antigo", "'2025-01-10'");
        insertNotice(student.id(), "HOLIDAY_CREATED", "recente", "now()");

        cleanupJob.deleteOldNotices();

        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(countLogs(student.id(), "HOLIDAY_CREATED")).isEqualTo(1);
    }

    /**
     * Depois da poda, o mesmo aviso pode ser gravado de novo — a constraint não
     * guarda memória do que foi apagado. É o motivo de a retenção precisar ser
     * folgadamente maior que a janela de reentrega: sem isso, podar reabriria a
     * porta para reenviar o que já saiu.
     */
    @Test
    void prunedNoticeStopsBlockingTheSameReference() {
        Student student = seedStudent("Aluno Do Historico Podado");
        insertNotice(student.id(), "CLASS_CANCELED", "2025-01-10", "'2025-01-10'");

        cleanupJob.deleteOldNotices();
        insertNotice(student.id(), "CLASS_CANCELED", "2025-01-10", "now()");

        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
    }

    private void insertNotice(long studentId, String template, String referenceKey, String sentAt) {
        jdbcTemplate.update("""
                INSERT INTO notification_log
                    (student_id, template, reference_key, sent_at, created_at, updated_at)
                VALUES (?, ?, ?, %s, now(), now())
                """.formatted(sentAt), studentId, template, referenceKey);
    }
}
