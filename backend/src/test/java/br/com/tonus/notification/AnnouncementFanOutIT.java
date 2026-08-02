package br.com.tonus.notification;

import br.com.tonus.scheduling.AnnouncementCreated;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comunicado é o único evento que vira N mensagens, e é onde a transação por aluno
 * se paga: sem ela, falhar no meio desfaria as linhas já gravadas e o
 * reprocessamento reenviaria tudo — falha parcial virando duplicação em massa.
 *
 * As asserções olham só para os alunos que cada teste semeia. O fan-out avisa
 * todo aluno ativo do banco, inclusive os que outras classes deixaram para
 * trás, e contar o total amarraria estes testes ao resto da suíte.
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
     * A mensagem depende do efeito. "Não teremos aula" e "as aulas acontecem
     * normalmente" são avisos opostos, e mandar o primeiro para um comunicado
     * que não suspende esvaziaria a sala num dia de aula.
     *
     * O template também é outro, e não só o texto: ele é a chave de
     * idempotência junto com a data, e é o que permite ao aluno receber o aviso
     * certo sem que uma reentrega mande o outro.
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
     * O template novo também é idempotente. A entrega é at-least-once e o
     * `ANNOUNCEMENT_NOTICE` nasceu nesta mudança: a garantia velha era do
     * template de feriado, e um caminho novo sem a mesma prova é onde a
     * duplicação aparece.
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
     * Os dois avisos do mesmo dia convivem no log, porque o template entra na
     * chave. Não é hipótese: apagar um comunicado e recriá-lo com o outro
     * efeito republica o evento para a mesma data, e chavear só pela data faria
     * o aluno continuar acreditando que não teria aula.
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

    /** E a suspensão diz o contrário, com o motivo junto. */
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
     * Critério de aceite: falha no segundo aluno preserva a linha do primeiro.
     * É a prova de que cada aluno commita por conta própria.
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
     * O reprocessamento depois de uma falha parcial só avisa quem ficou de
     * fora. É a razão de a UNIQUE existir: o registry reentrega o evento
     * inteiro, e quem já recebeu tem de ser barrado no banco.
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
     * Gateway fora do ar aborta o laço em vez de percorrer o estúdio inteiro
     * colecionando timeouts.
     *
     * É o único teste que precisa da população ativa sob controle — o limite
     * conta falhas *seguidas*, e um aluno de outra classe entregue no meio
     * zeraria o contador. Por isso desativa e devolve tudo no fim.
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

            // O limite default é 5: a sexta tentativa não acontece. A espera é
            // por "pelo menos 5" e a asserção é por "exatamente 5" — esperar
            // por igualdade passaria de raspão se o laço estivesse percorrendo
            // os oito e fosse observado justo ao cruzar o quinto.
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
