package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.NotificationProperties.Evolution;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qual adapter sobe, e quando a aplicação recusa subir.
 *
 * A escolha do adapter é decisão de deploy que nenhum outro teste exercita: a
 * suíte inteira roda com o envio desligado, então sem isto o caminho de
 * produção só seria conferido em produção.
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

    /** Os defaults valem mesmo quando nada é configurado. */
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
     * Retenção curta demais é recusada na subida.
     *
     * O histórico é a chave de idempotência e a reentrega vai até 24h; podar
     * antes disso reenvia mensagem já entregue. Os dois valores vivem em
     * módulos diferentes e nada além deste piso os relaciona — é o único lugar
     * onde a configuração perigosa dá para ser barrada.
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
                new Evolution(baseUrl, apiKey, "tonus",
                        Duration.ofSeconds(3), Duration.ofSeconds(10), enabled),
                5, null);
    }
}
