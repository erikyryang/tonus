package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pruning job passes through the configured retention, for both what
 * completed and what was abandoned.
 *
 * The parameter is what separates "deletes what's no longer useful" from
 * "deletes the answer to whether the notice went out" — and it's a
 * deletion, so getting this wrong has no undo.
 */
class EventPublicationCleanupJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void deletesCompletedPublicationsOlderThanTheConfiguredRetention() {
        CapturingPublications publications = new CapturingPublications();
        StubRepository repository = new StubRepository();

        new EventPublicationCleanupJob(publications, repository,
                new EventRegistryProperties(null, null, Duration.ofDays(30)), FIXED_CLOCK)
                .deleteOldPublications();

        assertThat(publications.captured.get()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void defaultRetentionIsSevenDays() {
        CapturingPublications publications = new CapturingPublications();
        StubRepository repository = new StubRepository();

        new EventPublicationCleanupJob(publications, repository,
                new EventRegistryProperties(null, null, null), FIXED_CLOCK)
                .deleteOldPublications();

        assertThat(publications.captured.get()).isEqualTo(Duration.ofDays(7));
    }

    /**
     * The abandoned rows — never completed, past max-age — are what used to
     * grow the registry without bound (an event with no consumer never
     * completes). They get the same retention as the completed ones.
     */
    @Test
    void deletesAbandonedIncompletePublicationsOlderThanRetention() {
        CapturingPublications publications = new CapturingPublications();
        StubRepository repository = new StubRepository();
        UUID abandoned = UUID.randomUUID();
        UUID stillInside = UUID.randomUUID();
        repository.incompleteBefore.add(publication(abandoned, Instant.parse("2026-05-01T00:00:00Z")));
        repository.incompleteBefore.add(publication(stillInside, Instant.parse("2026-07-20T00:00:00Z")));

        new EventPublicationCleanupJob(publications, repository,
                new EventRegistryProperties(null, null, Duration.ofDays(30)), FIXED_CLOCK)
                .deleteOldPublications();

        assertThat(repository.cutoff.get()).isEqualTo(Instant.parse("2026-06-27T12:00:00Z"));
        assertThat(repository.deleted.get()).containsExactly(abandoned);
    }

    @Test
    void deletesNothingWhenThereAreNoAbandonedPublications() {
        CapturingPublications publications = new CapturingPublications();
        StubRepository repository = new StubRepository();

        new EventPublicationCleanupJob(publications, repository,
                new EventRegistryProperties(null, null, Duration.ofDays(30)), FIXED_CLOCK)
                .deleteOldPublications();

        assertThat(repository.deleted.get()).isEmpty();
    }

    private static TargetEventPublication publication(UUID id, Instant publishedAt) {
        return new TargetEventPublication() {

            @Override
            public UUID getIdentifier() {
                return id;
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

            @Override
            public PublicationTargetIdentifier getTargetIdentifier() {
                return PublicationTargetIdentifier.of("teste");
            }

            @Override
            public void markCompleted(Instant completionDate) {
                // stub: nothing to complete
            }
        };
    }

    private static final class CapturingPublications implements CompletedEventPublications {

        private final AtomicReference<Duration> captured = new AtomicReference<>();

        @Override
        public Collection<? extends EventPublication> findAll() {
            return List.of();
        }

        @Override
        public void deletePublications(Predicate<EventPublication> filter) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public void deletePublicationsOlderThan(Duration duration) {
            captured.set(duration);
        }
    }

    private static final class StubRepository implements EventPublicationRepository {

        private final AtomicReference<Instant> cutoff = new AtomicReference<>();
        private final AtomicReference<List<UUID>> deleted = new AtomicReference<>(new ArrayList<>());
        private final List<TargetEventPublication> incompleteBefore = new ArrayList<>();

        @Override
        public TargetEventPublication create(TargetEventPublication publication) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public void markCompleted(Object event, PublicationTargetIdentifier targetIdentifier, Instant completionDate) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public void markCompleted(UUID identifier, Instant completionDate) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public List<TargetEventPublication> findIncompletePublications() {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant cutoff) {
            this.cutoff.set(cutoff);
            return incompleteBefore.stream()
                    .filter(publication -> publication.getPublicationDate().isBefore(cutoff))
                    .toList();
        }

        @Override
        public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
                Object event, PublicationTargetIdentifier targetIdentifier) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public void deletePublications(List<UUID> identifiers) {
            deleted.set(identifiers);
        }

        @Override
        public void deleteCompletedPublications() {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }

        @Override
        public void deleteCompletedPublicationsBefore(Instant completionDate) {
            throw new UnsupportedOperationException("o job não usa este caminho");
        }
    }
}