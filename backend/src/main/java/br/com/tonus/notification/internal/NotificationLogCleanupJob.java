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
 * Prunes the history of delivered notices.
 *
 * The table gains one row per notice per student and never lost any, while
 * `event_publication` was already being pruned — an asymmetry that hadn't
 * been decided, just forgotten.
 *
 * The pruning is only safe because redelivery has a ceiling. `notification_log`
 * is the idempotency key: deleting the row of a notice whose publication
 * could still be redelivered makes the student receive the message again.
 * What guarantees the ceiling is `EventRepublicationJob` (24h by default)
 * plus `spring.modulith.events.republish-outstanding-events-on-restart=false`
 * — with startup republication turned on there would be no ceiling at all,
 * because it redelivers everything incomplete without looking at age, and
 * this pruning would be dangerous.
 *
 * The case that makes this concrete is the holiday fan-out: the rows of
 * students already notified get recorded while the publication stays
 * incomplete because of the ones that were missed.
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

    /** After the other nightly sweeps, to not compete with them for the database. */
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
