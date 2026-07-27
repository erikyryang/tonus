package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.NotificationLogRepository;
import java.time.Clock;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Poda o histórico de avisos entregues.
 *
 * A tabela ganha uma linha por aviso por aluno e nunca perdia nenhuma, enquanto
 * `event_publication` já era podada — assimetria que não tinha sido decidida,
 * só esquecida.
 *
 * A poda só é segura porque a reentrega tem teto. `notification_log` é a chave
 * de idempotência: apagar a linha de um aviso cuja publicação ainda possa ser
 * reentregue faz o aluno receber a mensagem outra vez. Quem garante o teto é
 * `EventRepublicationJob` (24h por padrão) somado a
 * `spring.modulith.events.republish-outstanding-events-on-restart=false` — com
 * a republicação de subida ligada não haveria teto nenhum, porque ela reentrega
 * tudo que está incompleto sem olhar idade, e esta poda seria perigosa.
 *
 * O caso que torna isso concreto é o fan-out de feriado: as linhas dos alunos
 * já avisados ficam gravadas enquanto a publicação segue incompleta por causa
 * dos que faltaram.
 */
@Component
public class NotificationLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogCleanupJob.class);

    private final NotificationLogRepository logs;
    private final NotificationProperties properties;
    private final Clock clock;

    NotificationLogCleanupJob(NotificationLogRepository logs, NotificationProperties properties, Clock clock) {
        this.logs = logs;
        this.properties = properties;
        this.clock = clock;
    }

    /** Depois das outras varreduras noturnas, para não disputar o banco com elas. */
    @Scheduled(cron = "${notification.cleanup-cron:0 45 3 * * *}")
    @SchedulerLock(name = "notification-log-cleanup-job", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    @Transactional
    public void deleteOldNotices() {
        Instant threshold = clock.instant().minus(properties.logRetention());
        int deleted = logs.deleteSentBefore(threshold);
        if (deleted > 0) {
            log.info("{} aviso(s) anteriores a {} removidos do histórico", deleted, threshold);
        }
    }
}
