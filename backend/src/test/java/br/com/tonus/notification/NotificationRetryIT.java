package br.com.tonus.notification;

import br.com.tonus.scheduling.ClassCanceled;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import static br.com.tonus.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * O aluno acaba sendo avisado, mesmo quando o primeiro envio falha.
 *
 * Existe porque o teste anterior parava cedo demais: afirmava que a publicação
 * ficava incompleta e dava por encerrado. Ficar incompleta é o mecanismo, não o
 * resultado — e o mecanismo estava certo enquanto ninguém reprocessava, de modo
 * que a suíte ficava verde com o aviso nunca saindo. Teste que descreve o meio
 * em vez do fim não protege o que interessa.
 *
 * Reprocessa pela API do Modulith, e não pelo EventRepublicationJob: o job mora
 * em shared e quem o aciona em produção é o cron. O que precisa ser verdade
 * aqui é que a reentrega chega ao aluno; que o job passa a idade configurada é
 * assunto de {@code EventRepublicationJobTest}.
 */
class NotificationRetryIT extends AbstractNotificationIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");

    @Autowired
    private IncompleteEventPublications incompletePublications;

    @Test
    void studentIsNotifiedAfterTheFailedDeliveryIsReprocessed() {
        Student student = seedStudent("Aluno Da Segunda Chance");
        sender.failFor(student.phone());

        publish(new ClassCanceled(student.id(), MONDAY));
        eventually("primeira tentativa falhou",
                () -> sender.countAttemptsTo(student.phone()) >= 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isZero();
        assertThat(countIncompletePublications("ClassCanceled", student.id())).isEqualTo(1);

        // A Evolution volta, e o job varre o registry.
        sender.reset();
        incompletePublications.resubmitIncompletePublications(publication -> true);

        eventually("aviso entregue no reprocessamento",
                () -> sender.countSentTo(student.phone()) == 1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
        eventually("publicação finalmente concluída",
                () -> countIncompletePublications("ClassCanceled", student.id()) == 0);
    }

    /**
     * Reprocessar não reenvia o que já saiu. É a mesma constraint de sempre,
     * agora exercitada pelo caminho que o job usa — que é por onde um reenvio
     * em massa aconteceria se a idempotência falhasse.
     */
    @Test
    void reprocessingDoesNotResendWhatAlreadyWentOut() {
        Student student = seedStudent("Aluno Ja Avisado No Retry");

        publish(new ClassCanceled(student.id(), MONDAY));
        eventually("aviso entregue", () -> sender.countSentTo(student.phone()) == 1);
        eventually("entrega concluída",
                () -> countIncompletePublications("ClassCanceled", student.id()) == 0);

        incompletePublications.resubmitIncompletePublications(publication -> true);

        assertThat(sender.countSentTo(student.phone())).isEqualTo(1);
        assertThat(countLogs(student.id(), "CLASS_CANCELED")).isEqualTo(1);
    }
}
