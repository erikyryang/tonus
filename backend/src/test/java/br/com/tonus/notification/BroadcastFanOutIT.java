package br.com.tonus.notification;

import br.com.tonus.scheduling.BroadcastRequested;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual dispatch (change 9): same fan-out as the announcement, keyed by the
 * broadcastId instead of the date — there is no domain fact behind the
 * dispatch, and the ADMIN can legitimately repeat it on the same day.
 */
class BroadcastFanOutIT extends AbstractNotificationIntegrationTest {

    @Test
    void broadcastNotifiesEveryActiveStudent() {
        Student first = seedStudent("Aluno Disparo Um");
        Student second = seedStudent("Aluno Disparo Dois");

        publish(new BroadcastRequested("broadcast-1", "Aula de sábado cancelada por manutenção."));

        eventually("os dois alunos avisados", () -> sender.countSentTo(first.phone()) == 1
                && sender.countSentTo(second.phone()) == 1);
        assertThat(countLogs(first.id(), "BROADCAST")).isEqualTo(1);
        assertThat(countLogs(second.id(), "BROADCAST")).isEqualTo(1);
        assertThat(sender.lastMessageTo(first.phone())).contains("Aula de sábado cancelada por manutenção.");
    }

    @Test
    void reprocessingSameBroadcastDoesNotSendTwice() {
        Student student = seedStudent("Aluno Disparo Reprocessado");

        publish(new BroadcastRequested("broadcast-2", "Aviso reprocessado"));
        eventually("primeira entrega", () -> sender.countSentTo(student.phone()) == 1);

        publish(new BroadcastRequested("broadcast-2", "Aviso reprocessado"));

        eventually("o listener rodou de novo", () -> sender.countAttemptsTo(student.phone()) >= 1);
        assertThat(sender.countSentTo(student.phone())).isEqualTo(1);
        assertThat(countLogs(student.id(), "BROADCAST")).isEqualTo(1);
    }

    /**
     * The core point of the design: keying by the broadcastId, not by the
     * date, is what lets two legitimate dispatches on the same day both reach
     * the student — keying by date would make the second one be swallowed as
     * if it were a redelivery of the first.
     */
    @Test
    void twoDifferentBroadcastsSameDayBothReachTheStudent() {
        Student student = seedStudent("Aluno Disparo Duplo Legitimo");

        publish(new BroadcastRequested("broadcast-3", "Primeiro aviso do dia"));
        eventually("primeiro aviso entregue", () -> sender.countSentTo(student.phone()) == 1);

        publish(new BroadcastRequested("broadcast-4", "Segundo aviso do mesmo dia"));

        eventually("segundo aviso entregue", () -> sender.countSentTo(student.phone()) == 2);
        assertThat(countLogs(student.id(), "BROADCAST")).isEqualTo(2);
    }

    @Test
    void failureOnOneStudentPreservesTheOthers() {
        Student first = seedStudent("Aluno Disparo Preservado");
        Student failing = seedStudent("Aluno Disparo Que Falha");
        sender.failFor(failing.phone());

        publish(new BroadcastRequested("broadcast-5", "Aviso com falha parcial"));

        eventually("o aluno entregável foi avisado", () -> sender.countSentTo(first.phone()) == 1);
        assertThat(countLogs(first.id(), "BROADCAST")).isEqualTo(1);
        assertThat(countLogs(failing.id(), "BROADCAST")).isZero();
    }
}
