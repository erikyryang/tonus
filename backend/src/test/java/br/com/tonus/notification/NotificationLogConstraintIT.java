package br.com.tonus.notification;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The notification_log constraints, exercised directly against the database.
 *
 * The query in NotificationService avoids the resend in the common case, but
 * between querying and inserting there is a window, and delivery is
 * at-least-once: what closes that window is the UNIQUE. Testing by SQL, without
 * going through the application, is what proves the guarantee does not depend
 * on the code keeping on checking beforehand — same decision as
 * {@code DefaultScheduleUniquenessIT}.
 */
class NotificationLogConstraintIT extends AbstractNotificationIntegrationTest {

    @Test
    void sameNoticeForSameStudentTwiceIsRejected() {
        Student student = seedStudent("Aluno Da Constraint");
        insertLog(student.id(), "CLASS_CANCELED", "2026-07-27");

        assertThatThrownBy(() -> insertLog(student.id(), "CLASS_CANCELED", "2026-07-27"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * The key is the whole triple. Holiday and cancellation on the same day are
     * two different notices, and the student needs both.
     */
    @Test
    void differentTemplatesShareTheSameReference() {
        Student student = seedStudent("Aluno Dois Avisos");
        insertLog(student.id(), "CLASS_CANCELED", "2026-07-27");

        assertThatCode(() -> insertLog(student.id(), "ANNOUNCEMENT_SUSPENSION", "2026-07-27"))
                .doesNotThrowAnyException();
    }

    @Test
    void sameNoticeForDifferentStudentsIsAllowed() {
        Student first = seedStudent("Aluno Feriado A");
        Student second = seedStudent("Aluno Feriado B");
        insertLog(first.id(), "ANNOUNCEMENT_SUSPENSION", "2026-12-25");

        assertThatCode(() -> insertLog(second.id(), "ANNOUNCEMENT_SUSPENSION", "2026-12-25"))
                .doesNotThrowAnyException();
    }

    /**
     * A template outside the list would have no meaning at all, same reason as
     * the CHECK on invoice.status. Without it, a typo would become a dead row
     * that never matches the idempotency query — and silent resending.
     */
    @Test
    void unknownTemplateIsRejected() {
        Student student = seedStudent("Aluno Template Invalido");

        assertThatThrownBy(() -> insertLog(student.id(), "CLASS_POSTPONED", "2026-07-27"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertLog(long studentId, String template, String referenceKey) {
        jdbcTemplate.update("""
                INSERT INTO notification_log
                    (student_id, template, reference_key, sent_at, created_at, updated_at)
                VALUES (?, ?, ?, now(), now(), now())
                """, studentId, template, referenceKey);
    }
}
