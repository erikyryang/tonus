package br.com.tonus.notification.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the notification module.
 *
 * Nested like {@code AuthProperties}: {@code evolution} describes the
 * adapter and {@code consecutiveFailureLimit} is send policy, which applies
 * to any provider — mixing the two under a single prefix would make the
 * limit look like an Evolution detail.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Evolution evolution, Integer consecutiveFailureLimit, Duration logRetention) {

    /**
     * Floor for the history's retention. The table is the idempotency key:
     * deleting a row whose publication could still be redelivered makes the
     * student receive the message again. Redelivery is bounded by
     * `shared.events.max-age` (24h by default), and this floor exists so
     * the gap between the two is too large for someone to bump into it by
     * accident while adjusting configuration — the two values live in
     * different modules and nothing beyond this relates them.
     */
    private static final Duration MINIMUM_LOG_RETENTION = Duration.ofDays(7);

    public NotificationProperties {
        if (evolution == null) {
            evolution = new Evolution(null, null, null, null, null, false);
        }
        // Consecutive failures in a fan-out aren't bad luck, they're the
        // gateway being down. Five is loose enough to tolerate an isolated
        // invalid number and short enough to not waste an hour of timeouts.
        if (consecutiveFailureLimit == null) {
            consecutiveFailureLimit = 5;
        }
        // Six months: well beyond any possible redelivery, and the horizon
        // within which it's still reasonable to ask whether a notice went out.
        if (logRetention == null) {
            logRetention = Duration.ofDays(180);
        }
        if (logRetention.compareTo(MINIMUM_LOG_RETENTION) < 0) {
            throw new IllegalArgumentException(
                    "notification.log-retention não pode ser menor que %s: o histórico é a chave de "
                            + "idempotência, e podá-lo cedo demais reenvia mensagem já entregue"
                            .formatted(MINIMUM_LOG_RETENTION));
        }
    }

    /**
     * The key and URL come from environment variables — the WhatsApp
     * session is a credential, not an application parameter.
     *
     * {@code enabled} starts false: without it, starting the application
     * locally would require a real WhatsApp session.
     */
    public record Evolution(
            String baseUrl,
            String apiKey,
            String instance,
            Duration connectTimeout,
            Duration readTimeout,
            boolean enabled) {

        public Evolution {
            if (instance == null) {
                instance = "tonus";
            }
            // Explicit timeout on both sides: without it, an Evolution that
            // accepts the connection and never responds hangs the listener forever.
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(3);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(10);
            }
        }
    }
}
