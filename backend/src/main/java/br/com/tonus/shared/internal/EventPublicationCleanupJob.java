package br.com.tonus.shared.internal;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes completed deliveries after the retention window.
 *
 * The registry keeps one row per event per listener and never deletes
 * anything on its own. With six notices per student per month, plus
 * registration events, the table only grows — and it sits on the hot path of
 * every publication and of every scan by the republication job, which then
 * reads more and more garbage to find fewer and fewer pending items.
 *
 * Deletes only what's completed: what hasn't closed is exactly what
 * {@link EventRepublicationJob} needs to find.
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
     * At night and half an hour after the billing job: both touch a large
     * table, and pruning while billing publishes events would just create
     * unnecessary contention.
     */
    @Scheduled(cron = "${shared.events.cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "event-publication-cleanup-job", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    public void deleteCompletedPublications() {
        log.debug("Apagando entregas concluídas há mais de {}", properties.retention());
        completedPublications.deletePublicationsOlderThan(properties.retention());
    }
}
