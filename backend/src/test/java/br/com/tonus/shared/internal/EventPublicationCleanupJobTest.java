package br.com.tonus.shared.internal;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O job de poda repassa a retenção configurada.
 *
 * O parâmetro é o que separa "apaga o que já não serve" de "apaga a resposta
 * para se o aviso saiu" — e é apagamento, então errar aqui não tem desfazer.
 */
class EventPublicationCleanupJobTest {

    @Test
    void deletesPublicationsOlderThanTheConfiguredRetention() {
        CapturingPublications publications = new CapturingPublications();

        new EventPublicationCleanupJob(publications, new EventRegistryProperties(null, null, Duration.ofDays(30)))
                .deleteCompletedPublications();

        assertThat(publications.captured.get()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void defaultRetentionIsSevenDays() {
        CapturingPublications publications = new CapturingPublications();

        new EventPublicationCleanupJob(publications, new EventRegistryProperties(null, null, null))
                .deleteCompletedPublications();

        assertThat(publications.captured.get()).isEqualTo(Duration.ofDays(7));
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
}
