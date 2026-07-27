package br.com.tonus.notification;

import br.com.tonus.scheduling.HolidayCreated;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feriado é o único evento que vira N mensagens, e é onde a transação por aluno
 * se paga: sem ela, falhar no meio desfaria as linhas já gravadas e o
 * reprocessamento reenviaria tudo — falha parcial virando duplicação em massa.
 *
 * As asserções olham só para os alunos que cada teste semeia. O fan-out avisa
 * todo aluno ativo do banco, inclusive os que outras classes deixaram para
 * trás, e contar o total amarraria estes testes ao resto da suíte.
 */
class HolidayFanOutIT extends AbstractNotificationIntegrationTest {

    @Test
    void holidayNotifiesEveryActiveStudent() {
        LocalDate christmas = LocalDate.parse("2026-12-25");
        Student first = seedStudent("Aluno Feriado Um");
        Student second = seedStudent("Aluno Feriado Dois");
        Student third = seedStudent("Aluno Feriado Tres");

        publish(new HolidayCreated(christmas));

        eventually("os três alunos avisados", () -> sender.countSentTo(first.phone()) == 1
                && sender.countSentTo(second.phone()) == 1
                && sender.countSentTo(third.phone()) == 1);
        assertThat(countLogs(first.id(), "HOLIDAY_CREATED")).isEqualTo(1);
        assertThat(countLogs(second.id(), "HOLIDAY_CREATED")).isEqualTo(1);
        assertThat(countLogs(third.id(), "HOLIDAY_CREATED")).isEqualTo(1);
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

        publish(new HolidayCreated(carnival));

        eventually("os dois alunos entregáveis foram avisados",
                () -> sender.countSentTo(first.phone()) == 1 && sender.countSentTo(third.phone()) == 1);
        assertThat(countLogs(first.id(), "HOLIDAY_CREATED")).isEqualTo(1);
        assertThat(countLogs(third.id(), "HOLIDAY_CREATED")).isEqualTo(1);
        assertThat(countLogs(failing.id(), "HOLIDAY_CREATED")).isZero();
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

        publish(new HolidayCreated(laborDay));
        eventually("primeira passada avisou quem dava",
                () -> sender.countSentTo(delivered.phone()) == 1);

        sender.reset();
        publish(new HolidayCreated(laborDay));

        eventually("segunda passada avisou o pendente",
                () -> sender.countSentTo(pending.phone()) == 1);
        assertThat(sender.countSentTo(delivered.phone())).isZero();
        assertThat(countLogs(delivered.id(), "HOLIDAY_CREATED")).isEqualTo(1);
        assertThat(countLogs(pending.id(), "HOLIDAY_CREATED")).isEqualTo(1);
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

            publish(new HolidayCreated(LocalDate.parse("2026-09-07")));

            // O limite default é 5: a sexta tentativa não acontece. A espera é
            // por "pelo menos 5" e a asserção é por "exatamente 5" — esperar
            // por igualdade passaria de raspão se o laço estivesse percorrendo
            // os oito e fosse observado justo ao cruzar o quinto.
            eventually("cinco tentativas de envio", () -> sender.attempts().size() >= 5);
            assertThat(sender.attempts()).hasSize(5);
            assertThat(sender.sent()).isEmpty();
            assertThat(students).allSatisfy(student ->
                    assertThat(countLogs(student.id(), "HOLIDAY_CREATED")).isZero());
        } finally {
            reactivate(previouslyActive);
        }
    }
}
