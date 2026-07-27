package br.com.tonus.shared.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga entrega concluída depois da janela de retenção.
 *
 * O registry guarda uma linha por evento por listener e nunca apaga nada
 * sozinho. Com seis avisos por aluno por mês, mais os eventos de cadastro, a
 * tabela só cresce — e ela está no caminho quente de toda publicação e de toda
 * varredura do job de republicação, que passa a ler cada vez mais lixo para
 * achar cada vez menos pendência.
 *
 * Apaga só o que está concluído: o que não fechou é justamente o que o
 * {@link EventRepublicationJob} precisa encontrar.
 */
@Component
public class EventPublicationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationCleanupJob.class);

    private final CompletedEventPublications completedPublications;
    private final EventRegistryProperties properties;

    EventPublicationCleanupJob(CompletedEventPublications completedPublications,
                               EventRegistryProperties properties) {
        this.completedPublications = completedPublications;
        this.properties = properties;
    }

    /**
     * De madrugada e meia hora depois do job de cobrança: os dois mexem em
     * tabela grande, e a poda varrendo enquanto a cobrança publica eventos só
     * cria contenção sem necessidade.
     */
    @Scheduled(cron = "${shared.events.cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "event-publication-cleanup-job", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    public void deleteCompletedPublications() {
        log.debug("Apagando entregas concluídas há mais de {}", properties.retention());
        completedPublications.deletePublicationsOlderThan(properties.retention());
    }
}
