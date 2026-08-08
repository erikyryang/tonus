package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The redelivery window.
 *
 * The two edges exist for opposite reasons, and getting either wrong is
 * costly: the floor avoids redelivering to a listener that's still running;
 * the ceiling avoids a delivery lost for good being retried every five
 * minutes forever, since the registry keeps no attempt counter.
 */
class EventRepublicationJobTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void resubmitsWhatIsInsideTheWindow() {
        assertThat(shouldResubmit(NOW.minus(Duration.ofMinutes(30)))).isTrue();
    }

    /** Might still be a listener in the middle of working. */
    @Test
    void skipsWhatIsTooYoung() {
        assertThat(shouldResubmit(NOW.minus(Duration.ofMinutes(1)))).isFalse();
    }

    /** Past the ceiling, it gives up — and the row stays in the database as evidence. */
    @Test
    void skipsWhatIsTooOld() {
        assertThat(shouldResubmit(NOW.minus(Duration.ofDays(2)))).isFalse();
    }

    @Test
    void windowBoundariesComeFromConfiguration() {
        EventRegistryProperties tight =
                new EventRegistryProperties(Duration.ofMinutes(10), Duration.ofHours(2), null);

        assertThat(shouldResubmit(NOW.minus(Duration.ofMinutes(5)), tight)).isFalse();
        assertThat(shouldResubmit(NOW.minus(Duration.ofMinutes(30)), tight)).isTrue();
        assertThat(shouldResubmit(NOW.minus(Duration.ofHours(3)), tight)).isFalse();
    }

    @Test
    void defaultsAreFiveMinutesAndOneDay() {
        EventRegistryProperties defaults = new EventRegistryProperties(null, null, null);

        assertThat(defaults.minAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.maxAge()).isEqualTo(Duration.ofDays(1));
    }

    private boolean shouldResubmit(Instant publishedAt) {
        return shouldResubmit(publishedAt, new EventRegistryProperties(null, null, null));
    }

    private boolean shouldResubmit(Instant publishedAt, EventRegistryProperties properties) {
        CapturingPublications publications = new CapturingPublications();

        new EventRepublicationJob(publications, properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .republishIncompletePublications();

        return publications.filter.get().test(publicationAt(publishedAt));
    }

    private static EventPublication publicationAt(Instant publishedAt) {
        return new EventPublication() {

            @Override
            public UUID getIdentifier() {
                return UUID.randomUUID();
            }

            @Override
            public Object getEvent() {
                return "evento";
            }

            @Override
            public Instant getPublicationDate() {
                return publishedAt;
            }

            @Override
            public Optional<Instant> getCompletionDate() {
                return Optional.empty();
            }

            @Override
            public Status getStatus() {
                return Status.PUBLISHED;
            }

            @Override
            public Instant getLastResubmissionDate() {
                return null;
            }

            @Override
            public int getCompletionAttempts() {
                return 0;
            }
        };
    }

    private static final class CapturingPublications implements IncompleteEventPublications {

        private final AtomicReference<Predicate<EventPublication>> filter = new AtomicReference<>();

        @Override
        public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
            this.filter.set(filter);
        }

        @Override
        public void resubmitIncompletePublicationsOlderThan(Duration duration) {
            throw new UnsupportedOperationException("o job não usa este caminho: só tem piso");
        }

        @Override
        public void resubmitIncompletePublications(ResubmissionOptions options) {
            throw new UnsupportedOperationException("o job não usa este caminho: usa o Predicate direto");
        }
    }
}
