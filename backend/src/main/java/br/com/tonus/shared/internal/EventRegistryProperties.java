package br.com.tonus.shared.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two policies of the event registry: when to reprocess what didn't
 * close and for how long to keep what closed.
 *
 * {@code minAge} is what separates "delivery that failed" from "delivery
 * that's still running": the listener is asynchronous and its row stays
 * incomplete while it works. Republishing too soon delivers the same event
 * to a listener that hasn't finished yet — listeners are idempotent and can
 * take it, but it's wasted work and confusing logs.
 *
 * {@code retention} is how long a completed delivery remains queryable
 * before it's deleted. Without pruning the table only grows; with pruning
 * too short the answer to "did this notice actually go out?" disappears,
 * which is the first question when a student complains.
 */
@ConfigurationProperties(prefix = "shared.events")
public record EventRegistryProperties(Duration minAge, Duration maxAge, Duration retention) {

    public EventRegistryProperties {
        if (minAge == null) {
            minAge = Duration.ofMinutes(5);
        }
        // One day. A cancelled-class notice is worthless after that, and the
        // table has no attempt counter: without a cap, a delivery that fails
        // for good — disconnected phone, student who vanished — gets
        // retried every 5 minutes forever, ~105 thousand times a year.
        if (maxAge == null) {
            maxAge = Duration.ofDays(1);
        }
        if (retention == null) {
            retention = Duration.ofDays(7);
        }
    }
}
