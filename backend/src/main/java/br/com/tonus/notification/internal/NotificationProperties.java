package br.com.tonus.notification.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do módulo de notificação.
 *
 * Aninhada como {@code AuthProperties}: {@code evolution} descreve o adapter e
 * {@code consecutiveFailureLimit} é política de envio, que vale para qualquer
 * provedor — misturar os dois num prefixo só faria o limite parecer detalhe da
 * Evolution.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Evolution evolution, Integer consecutiveFailureLimit, Duration logRetention) {

    /**
     * Piso da retenção do histórico. A tabela é a chave de idempotência: apagar
     * uma linha cuja publicação ainda possa ser reentregue faz o aluno receber
     * a mensagem de novo. A reentrega é limitada por `shared.events.max-age`
     * (24h por padrão), e este piso existe para que a distância entre as duas
     * seja grande demais para alguém encostar nela por engano ao ajustar
     * configuração — os dois valores vivem em módulos diferentes e nada além
     * daqui os relaciona.
     */
    private static final Duration MINIMUM_LOG_RETENTION = Duration.ofDays(7);

    public NotificationProperties {
        if (evolution == null) {
            evolution = new Evolution(null, null, null, null, null, false);
        }
        // Falhas seguidas num fan-out não são azar, são o gateway fora do ar.
        // Cinco é folgado o bastante para tolerar um número inválido isolado e
        // curto o bastante para não gastar uma hora de timeouts.
        if (consecutiveFailureLimit == null) {
            consecutiveFailureLimit = 5;
        }
        // Seis meses: muito além de qualquer reentrega possível, e o horizonte
        // em que ainda se pergunta se um aviso saiu.
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
     * A chave e a URL vêm de variável de ambiente — a sessão de WhatsApp é
     * credencial, não parâmetro de aplicação.
     *
     * {@code enabled} nasce falso: sem ele, subir a aplicação local exigiria
     * uma sessão de WhatsApp de verdade.
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
            // Timeout explícito nos dois lados: sem ele, uma Evolution que
            // aceita a conexão e nunca responde trava o listener para sempre.
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(3);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(10);
            }
        }
    }
}
