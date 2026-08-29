package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redelivers what stayed pending in the event registry.
 *
 * Without this job, "at-least-once" would just be a phrase: the row in
 * event_publication would keep completion_date null forever and nobody
 * would read it. The concrete symptom was Evolution being down for ten
 * minutes and the notices from that period never going out — recorded as
 * pending, with nothing complaining.
 *
 * Lives in shared because the registry belongs to shared: republishing is
 * infrastructure of the event mechanism, not of whichever module happened
 * to fail the most. auth also consumes events, and a job inside
 * notification would be reprocessing someone else's delivery.
 *
 * Complements {@code spring.modulith.events.republish-outstanding-events-on-restart}:
 * that one covers what stayed pending while the application was down, this
 * one covers what fails while it's up — which is the common case.
 */
@Component
public class EventRepublicationJob {

    private static final Logger log = LoggerFactory.getLogger(EventRepublicationJob.class);

    private final IncompleteEventPublications incompletePublications;
    private final EventRegistryProperties properties;
    private final Clock clock;

    EventRepublicationJob(IncompleteEventPublications incompletePublications,
                          EventRegistryProperties properties, Clock clock) {
        this.incompletePublications = incompletePublications;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Redelivers only what's within the window.
     *
     * The floor ({@code minAge}) separates a delivery that failed from a
     * listener that's still working. The ceiling ({@code maxAge}) exists
     * because the registry keeps no attempt counter: without it, whatever
     * fails for good keeps being retried every round, indefinitely. Past the
     * ceiling, the row stays in the database with completion_date null — it's
     * the record that that notice never went out, and deleting it would hide
     * the only evidence of that.
     *
     * Uses the predicate, not {@code resubmitIncompletePublicationsOlderThan},
     * because that variant only has a floor.
     *
     * {@code lockAtLeastFor} zeroed on purpose: reprocessing again is
     * harmless — every listener is idempotent — and a time floor would make
     * the job untestable, which is the problem documented in
     * {@code BillingJobIT}.
     */
    @Scheduled(cron = "${shared.events.republish-cron:0 */5 * * * *}")
    @SchedulerLock(name = "event-republication-job", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    public void republishIncompletePublications() {
        Instant now = clock.instant();
        Instant oldestWorthRetrying = now.minus(properties.maxAge());
        Instant youngestWorthRetrying = now.minus(properties.minAge());
        AtomicInteger abandoned = new AtomicInteger();

        incompletePublications.resubmitIncompletePublications(publication -> {
            Instant publishedAt = publication.getPublicationDate();
            if (publishedAt.isBefore(oldestWorthRetrying)) {
                abandoned.incrementAndGet();
                return false;
            }
            return publishedAt.isBefore(youngestWorthRetrying);
        });

        if (abandoned.get() > 0) {
            // At WARN, and every round: these are notices that never reached
            // anyone. The alert only stops when someone looks at the rows and
            // resolves it — which is exactly the desired behavior for lost
            // delivery.
            log.warn("{} entrega(s) pendente(s) há mais de {} — desistido de reentregar; "
                            + "consultar event_publication com completion_date nulo",
                    abandoned.get(), properties.maxAge());
        }
    }
}
