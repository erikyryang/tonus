package br.com.tonus.notification;

import br.com.tonus.scheduling.BroadcastRequested;
import org.junit.jupiter.api.Test;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Disparo manual (mudança 9): mesmo fan-out do comunicado, chaveado pelo
 * broadcastId em vez da data — não há fato de domínio atrás do disparo, e o
 * ADMIN pode legitimamente repeti-lo no mesmo dia.
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
     * O ponto central do desenho: chavear pelo broadcastId, e não pela data,
     * é o que permite dois disparos legítimos no mesmo dia chegarem os dois —
     * chavear pela data faria o segundo ser engolido como se fosse reentrega
     * do primeiro.
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
