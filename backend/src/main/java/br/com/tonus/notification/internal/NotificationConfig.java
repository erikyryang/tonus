package br.com.tonus.notification.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Chooses the outbound adapter based on the configuration.
 *
 * The two conditions are explicit (`true` and `false` with matchIfMissing)
 * instead of one of them using @ConditionalOnMissingBean: within the same
 * configuration class, "missing" depends on the order the methods are
 * evaluated, and an innocent reordering would swap the adapter in
 * production without anyone noticing.
 */
@Configuration
class NotificationConfig {

    /**
     * Incomplete configuration brings down startup, instead of turning into
     * a failure on every send. Enabled without a URL or key, the system
     * would serve normally and only the notice to the student would fail —
     * silently, inside an asynchronous listener. Failing here is the same
     * error discovered at deploy time.
     */
    @Bean
    @ConditionalOnProperty(prefix = "notification.evolution", name = "enabled", havingValue = "true")
    WhatsAppSender evolutionApiSender(NotificationProperties properties) {
        NotificationProperties.Evolution evolution = properties.evolution();
        requireConfigured("notification.evolution.base-url", evolution.baseUrl());
        requireConfigured("notification.evolution.api-key", evolution.apiKey());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) evolution.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) evolution.readTimeout().toMillis());

        RestClient restClient = RestClient.builder()
                .baseUrl(evolution.baseUrl())
                .requestFactory(requestFactory)
                .build();

        return new EvolutionApiSender(restClient, evolution.apiKey(), evolution.instance());
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.evolution", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    WhatsAppSender loggingWhatsAppSender() {
        return new LoggingWhatsAppSender();
    }

    private static void requireConfigured(String property, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "%s é obrigatório quando notification.evolution.enabled=true".formatted(property));
        }
    }
}
