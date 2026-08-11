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
    WhatsAppSender evolutionApiSender(NotificationProperties properties,
            org.springframework.beans.factory.ObjectProvider<WhatsAppAdminService> admin) {
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

        // Resolved per send, and lazily: the table is the authority (SPEC-013)
        // and the configured instance is only the fallback for a context that
        // has no settings — which is what keeps this @Configuration testable on
        // its own, without dragging in a repository and a database.
        return new EvolutionApiSender(restClient, evolution.apiKey(), () -> {
            WhatsAppAdminService adminService = admin.getIfAvailable();
            return adminService == null ? evolution.instance() : adminService.currentInstance();
        });
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.evolution", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    WhatsAppSender loggingWhatsAppSender() {
        return new LoggingWhatsAppSender();
    }

    /**
     * Administering the session does NOT depend on
     * {@code notification.evolution.enabled}.
     *
     * Turning sending on is what one does *after* pairing. Gating the pairing
     * path on that flag would leave no possible order of operations: no session
     * without the flag, and no reason for the flag without a session. What it
     * does depend on is having somewhere to talk to — without base-url and
     * api-key the stand-in below answers 503.
     */
    @Bean
    WhatsAppSessionGateway whatsAppSessionGateway(NotificationProperties properties) {
        NotificationProperties.Evolution evolution = properties.evolution();
        if (!StringUtils.hasText(evolution.baseUrl()) || !StringUtils.hasText(evolution.apiKey())) {
            return new NotConfiguredSessionGateway();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) evolution.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) evolution.sessionReadTimeout().toMillis());

        return new EvolutionSessionGateway(
                RestClient.builder()
                        .baseUrl(evolution.baseUrl())
                        .requestFactory(requestFactory)
                        .build(),
                evolution.apiKey());
    }

    private static void requireConfigured(String property, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "%s é obrigatório quando notification.evolution.enabled=true".formatted(property));
        }
    }
}
