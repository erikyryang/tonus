package br.com.tonus.notification;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As constraints de notification_log, exercitadas direto no banco.
 *
 * A consulta em NotificationService evita o reenvio no caso comum, mas entre
 * consultar e gravar existe uma janela, e a entrega é at-least-once: o que
 * fecha essa janela é a UNIQUE. Testar por SQL, sem passar pela aplicação, é o
 * que prova que a garantia não depende do código continuar checando antes —
 * mesma decisão de {@code DefaultScheduleUniquenessIT}.
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
     * A chave é a trinca inteira. Feriado e cancelamento no mesmo dia são dois
     * avisos diferentes, e o aluno precisa dos dois.
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
     * Template fora da lista não teria significado nenhum, mesma razão do CHECK
     * de invoice.status. Sem ele, um erro de digitação viraria linha morta que
     * nunca casa com a consulta de idempotência — e reenvio silencioso.
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
