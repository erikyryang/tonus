package br.com.tonus.notification.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Escolhe o adapter de saída conforme a configuração.
 *
 * As duas condições são explícitas (`true` e `false` com matchIfMissing) em vez
 * de uma delas usar @ConditionalOnMissingBean: dentro de uma mesma classe de
 * configuração, o "missing" depende da ordem de avaliação dos métodos, e uma
 * reordenação inocente trocaria o adapter em produção sem ninguém notar.
 */
@Configuration
class NotificationConfig {

    /**
     * Configuração incompleta derruba a subida, em vez de virar falha em cada
     * envio. Ligado sem URL ou sem chave, o sistema atenderia normalmente e só
     * o aviso ao aluno falharia — em silêncio, dentro de um listener
     * assíncrono. Falhar aqui é o mesmo erro descoberto no deploy.
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
