package br.com.tonus.notification;

import br.com.tonus.notification.internal.NotificationLogCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pruning deletes old history and preserves the recent.
 *
 * The cutoff is by {@code sent_at}, not by the age of the row in the database:
 * it is the notice date that tells whether the corresponding publication can
 * still be redelivered, and it is that redelivery the row exists to block.
 */
class NotificationLogCleanupIT extends AbstractNotificationIntegrationTest {

    @Autowired
    private NotificationLogCleanupJob cleanupJob;

    @Test
    void deletesNoticesBeyondRetentionAndKeepsTheRest() {
        Student student = seedStudent("Aluno Do Historico");
        // The test clock is fixed; the dates are relative to it, and the
        // default retention is 180 days.
        insertNotice(student.id(), "CLASS_CANCELED", "antigo", "'2025-01-10'");
        insertNotice(student.id(), "ANNOUNCEMENT_SUSPENSION", "recente", "now()");

        cleanupJob.deleteOldNotices();

        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
    }

    /**
     * After the pruning, the same notice can be inserted again — the
     * constraint keeps no memory of what was deleted. That is why the retention
     * must be generously longer than the redelivery window: without it,
     * pruning would reopen the door to resending what already went out.
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
