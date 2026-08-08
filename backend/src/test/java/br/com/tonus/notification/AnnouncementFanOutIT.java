package br.com.tonus.notification;

import br.com.tonus.scheduling.AnnouncementCreated;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Announcement is the only event that turns into N messages, and that is where
 * the per-student transaction pays off: without it, failing mid-way would undo
 * already-inserted rows and reprocessing would resend everything — a partial
 * failure turning into mass duplication.
 *
 * The assertions look only at the students each test seeds. The fan-out notifies
 * every active student in the database, including ones other classes left
 * behind, and counting the total would couple these tests to the rest of the
 * suite.
 */
class AnnouncementFanOutIT extends AbstractNotificationIntegrationTest {

    @Test
    void announcementNotifiesEveryActiveStudent() {
        LocalDate christmas = LocalDate.parse("2026-12-25");
        Student first = seedStudent("Aluno Feriado Um");
        Student second = seedStudent("Aluno Feriado Dois");
        Student third = seedStudent("Aluno Feriado Tres");

        publish(new AnnouncementCreated(christmas, "Natal", true));

        eventually("os três alunos avisados", () -> sender.countSentTo(first.phone()) == 1
                && sender.countSentTo(second.phone()) == 1
                && sender.countSentTo(third.phone()) == 1);
        assertThat(countLogs(first.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(second.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(third.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
    }

    /**
     * The message depends on the effect. "There will be no class" and "classes
     * happen as usual" are opposite notices, and sending the first for an
     * announcement that does not suspend would empty the room on a class day.
     *
     * The template is also different, not just the text: it is the idempotency
     * key together with the date, and it is what lets the student receive the
     * right notice without a redelivery sending the other one.
     */
    @Test
    void announcementWithoutSuspensionSaysClassesHappenNormally() {
        LocalDate date = LocalDate.parse("2026-11-11");
        Student student = seedStudent("Aluno Do Aviso Sem Suspensao");

        publish(new AnnouncementCreated(date, "Obra na rua, entrada pelos fundos", false));

        eventually("o aluno avisado", () -> sender.countSentTo(student.phone()) == 1);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_NOTICE")).isEqualTo(1);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_SUSPENSION")).isZero();
        assertThat(sender.lastMessageTo(student.phone()))
                .contains("Obra na rua, entrada pelos fundos")
                .contains("normalmente")
                .doesNotContain("não teremos aula");
    }

    /**
     * The new template is also idempotent. Delivery is at-least-once and the
     * `ANNOUNCEMENT_NOTICE` was born in this change: the old guarantee was for
     * the holiday template, and a new path without the same proof is where
     * duplication shows up.
     */
    @Test
    void reprocessingANoticeDoesNotSendItTwice() {
        LocalDate date = LocalDate.parse("2026-11-13");
        Student student = seedStudent("Aluno Do Aviso Reprocessado");

        publish(new AnnouncementCreated(date, "Recepção em obras", false));
        eventually("primeira entrega", () -> sender.countSentTo(student.phone()) == 1);

        publish(new AnnouncementCreated(date, "Recepção em obras", false));

        eventually("o listener rodou de novo", () -> sender.countAttemptsTo(student.phone()) >= 1);
        assertThat(sender.countSentTo(student.phone())).isEqualTo(1);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_NOTICE")).isEqualTo(1);
    }

    /**
     * The two notices for the same day coexist in the log, because the template
     * is part of the key. This is not hypothetical: deleting an announcement
     * and recreating it with the other effect republishes the event for the
     * same date, and keying by date alone would keep the student believing
     * there would be no class.
     */
    @Test
    void suspensionAndNoticeForTheSameDayAreIndependent() {
        LocalDate date = LocalDate.parse("2026-11-14");
        Student student = seedStudent("Aluno Do Comunicado Corrigido");

        publish(new AnnouncementCreated(date, "Feriado municipal", true));
        eventually("aviso de suspensão entregue", () -> sender.countSentTo(student.phone()) == 1);

        publish(new AnnouncementCreated(date, "Afinal teremos aula", false));

        eventually("correção entregue", () -> sender.countSentTo(student.phone()) == 2);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_NOTICE")).isEqualTo(1);
    }

    /** And the suspension says the opposite, with the reason attached. */
    @Test
    void suspendingAnnouncementSaysThereIsNoClassAndWhy() {
        LocalDate date = LocalDate.parse("2026-11-12");
        Student student = seedStudent("Aluno Do Aviso Com Suspensao");

        publish(new AnnouncementCreated(date, "Feriado municipal", true));

        eventually("o aluno avisado", () -> sender.countSentTo(student.phone()) == 1);
        assertThat(countLogs(student.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(sender.lastMessageTo(student.phone()))
                .contains("Feriado municipal")
                .contains("não teremos aula");
    }

    /**
     * Acceptance criterion: failure on the second student preserves the first
     * one's row. This is the proof that each student commits on its own.
     */
    @Test
    void failureOnOneStudentPreservesTheOthers() {
        LocalDate carnival = LocalDate.parse("2026-02-17");
        Student first = seedStudent("Aluno Preservado");
        Student failing = seedStudent("Aluno Que Falha");
        Student third = seedStudent("Aluno Seguinte");
        sender.failFor(failing.phone());

        publish(new AnnouncementCreated(carnival, "Carnaval", true));

        eventually("os dois alunos entregáveis foram avisados",
                () -> sender.countSentTo(first.phone()) == 1 && sender.countSentTo(third.phone()) == 1);
        assertThat(countLogs(first.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(third.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(failing.id(), "ANNOUNCEMENT_SUSPENSION")).isZero();
    }

    /**
     * Reprocessing after a partial failure only notifies who was left out.
     * That is the reason the UNIQUE exists: the registry redelivers the whole
     * event, and whoever already received it has to be barred at the database.
     */
    @Test
    void reprocessingAfterPartialFailureOnlyNotifiesWhoIsMissing() {
        LocalDate laborDay = LocalDate.parse("2026-05-01");
        Student delivered = seedStudent("Aluno Ja Avisado");
        Student pending = seedStudent("Aluno Pendente");
        sender.failFor(pending.phone());

        publish(new AnnouncementCreated(laborDay, "Dia do Trabalho", true));
        eventually("primeira passada avisou quem dava",
                () -> sender.countSentTo(delivered.phone()) == 1);

        sender.reset();
        publish(new AnnouncementCreated(laborDay, "Dia do Trabalho", true));

        eventually("segunda passada avisou o pendente",
                () -> sender.countSentTo(pending.phone()) == 1);
        assertThat(sender.countSentTo(delivered.phone())).isZero();
        assertThat(countLogs(delivered.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
        assertThat(countLogs(pending.id(), "ANNOUNCEMENT_SUSPENSION")).isEqualTo(1);
    }

    /**
     * Gateway down aborts the loop instead of walking the whole studio
     * collecting timeouts.
     *
     * This is the only test that needs the active population under control —
     * the limit counts *consecutive* failures, and a student from another
     * class delivered in the middle would zero the counter. Hence it
     * deactivates and restores everything at the end.
     */
    @Test
    void consecutiveFailuresAbortTheFanOut() {
        List<Long> previouslyActive = deactivateExistingStudents();
        try {
            List<Student> students = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                Student student = seedStudent("Aluno Do Aborto " + i);
                sender.failFor(student.phone());
                students.add(student);
            }

            publish(new AnnouncementCreated(LocalDate.parse("2026-09-07"), "Independencia", true));

            // The default limit is 5: the sixth attempt never happens. The wait
            // is for "at least 5" and the assertion is for "exactly 5" —
            // waiting for equality would pass by a hair if the loop were
            // walking the eight and were observed right as it crossed the fifth.
            eventually("cinco tentativas de envio", () -> sender.attempts().size() >= 5);
            assertThat(sender.attempts()).hasSize(5);
            assertThat(sender.sent()).isEmpty();
            assertThat(students).allSatisfy(student ->
                    assertThat(countLogs(student.id(), "ANNOUNCEMENT_SUSPENSION")).isZero());
        } finally {
            reactivate(previouslyActive);
        }
    }
}
