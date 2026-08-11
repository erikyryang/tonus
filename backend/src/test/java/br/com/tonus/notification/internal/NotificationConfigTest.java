package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.NotificationProperties.Evolution;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which adapter comes up, and when the application refuses to start.
 *
 * The adapter choice is a deploy decision no other test exercises: the whole
 * suite runs with delivery disabled, so without this the production path would
 * only be checked in production.
 */
class NotificationConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfig.class);

    @Test
    void withoutConfigurationTheSenderIsTheLoggingOne() {
        runner.withBean(NotificationProperties.class, () -> properties(false, null, null))
                .run(context -> assertThat(context).getBean(WhatsAppSender.class)
                        .isInstanceOf(LoggingWhatsAppSender.class));
    }

    @Test
    void disabledExplicitlyIsAlsoTheLoggingOne() {
        runner.withPropertyValues("notification.evolution.enabled=false")
                .withBean(NotificationProperties.class, () -> properties(false, null, null))
                .run(context -> assertThat(context).getBean(WhatsAppSender.class)
                        .isInstanceOf(LoggingWhatsAppSender.class));
    }

    @Test
    void enabledAndConfiguredUsesEvolution() {
        runner.withPropertyValues("notification.evolution.enabled=true")
                .withBean(NotificationProperties.class,
                        () -> properties(true, "http://evolution:8080", "chave"))
                .run(context -> assertThat(context).getBean(WhatsAppSender.class)
                        .isInstanceOf(EvolutionApiSender.class));
    }

    @Test
    void enabledWithoutBaseUrlRefusesToStart() {
        runner.withPropertyValues("notification.evolution.enabled=true")
                .withBean(NotificationProperties.class, () -> properties(true, null, "chave"))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("notification.evolution.base-url"));
    }

    @Test
    void enabledWithoutApiKeyRefusesToStart() {
        runner.withPropertyValues("notification.evolution.enabled=true")
                .withBean(NotificationProperties.class,
                        () -> properties(true, "http://evolution:8080", "  "))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("notification.evolution.api-key"));
    }

    /** The defaults hold even when nothing is configured. */
    @Test
    void unconfiguredPropertiesFallBackToDefaults() {
        NotificationProperties defaults = new NotificationProperties(null, null, null);

        assertThat(defaults.consecutiveFailureLimit()).isEqualTo(5);
        assertThat(defaults.evolution().enabled()).isFalse();
        assertThat(defaults.evolution().instance()).isEqualTo("tonus");
        assertThat(defaults.evolution().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(defaults.evolution().readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(defaults.logRetention()).isEqualTo(Duration.ofDays(180));
    }

    /**
     * Retention too short is refused at startup.
     *
     * The history is the idempotency key and the redelivery goes up to 24h;
     * pruning before that resends already-delivered messages. The two values
     * live in different modules and nothing but this floor relates them — it is
     * the only place where the dangerous configuration can be blocked.
     */
    @Test
    void retentionShorterThanTheGuardIsRejected() {
        assertThatThrownBy(() -> new NotificationProperties(null, null, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notification.log-retention");
    }

    @Test
    void retentionAtTheGuardIsAccepted() {
        assertThat(new NotificationProperties(null, null, Duration.ofDays(7)).logRetention())
                .isEqualTo(Duration.ofDays(7));
    }

    private NotificationProperties properties(boolean enabled, String baseUrl, String apiKey) {
        return new NotificationProperties(
                new Evolution(baseUrl, apiKey, "tonus", "2.3000.1043857760",
                        Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(60), enabled),
                5, null);
    }
}
