package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes deliveries after the retention window — both the completed ones and
 * the abandoned ones.
 *
 * The registry keeps one row per event per listener and never deletes
 * anything on its own. With six notices per student per month, plus
 * registration events, the table only grows — and it sits on the hot path of
 * every publication and of every scan by the republication job, which then
 * reads more and more garbage to find fewer and fewer pending items.
 *
 * Deletes what's completed after {@code retention}, as before, and what was
 * never completed (the abandoned rows). A row is abandoned once it's past
 * {@code max-age}: the republication job stops retrying it, and keeping it
 * beyond {@code retention} is what used to grow the table without bound —
 * every event without a consumer (the ones notification has not caught up to
 * yet) never completes and never ages out. The two windows are the same on
 * purpose: {@code max-age} decides how long we keep trying, {@code retention}
 * decides how long the record stays answerable.
 */
@Component
public class EventPublicationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationCleanupJob.class);

    private final CompletedEventPublications completedPublications;
    private final EventPublicationRepository repository;
    private final EventRegistryProperties properties;
    private final Clock clock;

    EventPublicationCleanupJob(CompletedEventPublications completedPublications,
                               EventPublicationRepository repository,
                               EventRegistryProperties properties, Clock clock) {
        this.completedPublications = completedPublications;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * At night and half an hour after the billing job: both touch a large
     * table, and pruning while billing publishes events would just create
     * unnecessary contention.
     */
    @Scheduled(cron = "${shared.events.cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "event-publication-cleanup-job", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    public void deleteOldPublications() {
        log.debug("Apagando entregas concluídas há mais de {}", properties.retention());
        completedPublications.deletePublicationsOlderThan(properties.retention());

        deleteAbandonedPublications();
    }

    private void deleteAbandonedPublications() {
        Instant abandonedBefore = clock.instant().minus(properties.retention());
        List<UUID> abandoned = repository.findIncompletePublicationsPublishedBefore(abandonedBefore).stream()
                .map(publication -> publication.getIdentifier())
                .toList();
        if (abandoned.isEmpty()) {
            return;
        }
        log.info("Apagando {} entrega(s) abandonada(s) há mais de {}", abandoned.size(), properties.retention());
        repository.deletePublications(abandoned);
    }
}