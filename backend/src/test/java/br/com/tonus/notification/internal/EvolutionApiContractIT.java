package br.com.tonus.notification.internal;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * O adapter contra a Evolution API de verdade.
 *
 * {@code EvolutionApiSenderTest} usa MockRestServiceServer e responde o que a
 * gente mandar responder — ele prova que o adapter monta a requisição que a
 * gente acha certa, não que a Evolution concorda. A diferença não é teórica:
 * o campo {@code number} estava indo em E.164 com "+" quando a API documenta
 * dígitos puros, e nenhum teste com dublê pegaria isso.
 *
 * Não sobe contexto Spring: o que está sob teste é HTTP, e subir a aplicação
 * inteira só somaria tempo a um teste que já paga um container de 1,75 GB.
 *
 * O que NÃO dá para cobrir aqui é o envio bem-sucedido — exige uma sessão de
 * WhatsApp pareada por QR code, com um celular do outro lado. O limite deste
 * teste é a última resposta antes disso, e por isso os três casos abaixo são
 * todos de falha: o valor está em serem as falhas REAIS da API, com os códigos
 * e as rotas dela.
 */
class EvolutionApiContractIT {

    private static final String API_KEY = "chave-de-contrato";
    private static final String INSTANCE = "tonus";

    private static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> EVOLUTION_DB =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("evolution")
                    .withUsername("tonus")
                    .withPassword("tonus")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("db");

    /**
     * Mesma imagem e mesmas variáveis do docker-compose. É metade do valor
     * deste teste: se a tag sumir ou uma variável for renomeada, quebra aqui e
     * não no deploy. Foi assim que se descobriu que {@code atendai/evolution-api}
     * — o repositório antigo do produto — nem existe mais para pull.
     */
    @SuppressWarnings("resource")
    private static final GenericContainer<?> EVOLUTION =
            new GenericContainer<>("evoapicloud/evolution-api:v2.3.7")
                    .withNetwork(NETWORK)
                    .dependsOn(EVOLUTION_DB)
                    .withEnv("AUTHENTICATION_API_KEY", API_KEY)
                    .withEnv("DATABASE_PROVIDER", "postgresql")
                    .withEnv("DATABASE_CONNECTION_URI",
                            "postgresql://tonus:tonus@db:5432/evolution?schema=public")
                    .withEnv("CACHE_REDIS_ENABLED", "false")
                    .withEnv("CACHE_LOCAL_ENABLED", "true")
                    .withEnv("SERVER_URL", "http://localhost:8080")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/").forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(5)));

    private static String baseUrl;
    private static RestClient rawClient;

    @BeforeAll
    static void startEvolution() {
        EVOLUTION.start();
        baseUrl = "http://%s:%d".formatted(EVOLUTION.getHost(), EVOLUTION.getMappedPort(8080));
        rawClient = RestClient.builder().baseUrl(baseUrl).build();

        // A instância nasce com status "close": existe no banco, sem sessão de
        // WhatsApp. É o estado mais próximo de produção que dá para alcançar
        // sem parear um celular.
        rawClient.post()
                .uri("/instance/create")
                .header("apikey", API_KEY)
                .body("""
                        {"instanceName":"%s","integration":"WHATSAPP-BAILEYS","qrcode":false}
                        """.formatted(INSTANCE))
                .header("Content-Type", "application/json")
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * A rota que o **adapter** monta é a que a Evolution atende.
     *
     * A asserção é sobre o corpo da resposta, e não sobre o status: caminho
     * errado também daria 404, só que de roteamento. Este 404 vem de dentro do
     * handler de sendText reclamando da instância — prova que a URI, o header e
     * o corpo construídos pelo adapter chegaram ao lugar certo.
     *
     * Aponta para uma instância inexistente de propósito: é o único jeito de
     * fazer a API responder algo específico da rota sem uma sessão pareada.
     */
    @Test
    void adapterReachesTheRealSendTextRoute() {
        EvolutionApiSender sender = new EvolutionApiSender(
                RestClient.builder().baseUrl(baseUrl).build(), API_KEY, "instancia-que-nao-existe");

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .cause()
                .isInstanceOf(RestClientResponseException.class)
                .asInstanceOf(throwable(RestClientResponseException.class))
                .satisfies(failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(404);
                    assertThat(failure.getResponseBodyAsString()).contains("instance does not exist");
                });
    }

    /** {@code apikey} é mesmo o mecanismo de autenticação, e no header. */
    @Test
    void withoutTheApiKeyHeaderEverythingIsUnauthorized() {
        HttpStatusCode withoutKey = rawClient.get()
                .uri("/instance/fetchInstances")
                .exchange((request, response) -> response.getStatusCode());

        HttpStatusCode withKey = rawClient.get()
                .uri("/instance/fetchInstances")
                .header("apikey", API_KEY)
                .exchange((request, response) -> response.getStatusCode());

        assertThat(withoutKey.value()).isEqualTo(401);
        assertThat(withKey.is2xxSuccessful()).isTrue();
    }

    /**
     * O adapter de produção, contra o servidor de verdade.
     *
     * Sem sessão de WhatsApp a Evolution devolve 500, e o que precisa ser
     * verdade é que isso vire {@link WhatsAppDeliveryException} — é dela que
     * dependem o rollback do registro e o reprocessamento. Um erro escapando em
     * outro tipo passaria por falha de programação e a mensagem se perderia.
     */
    @Test
    void adapterTurnsARealFailureIntoDeliveryException() {
        EvolutionApiSender sender = new EvolutionApiSender(
                RestClient.builder().baseUrl(baseUrl).build(), API_KEY, INSTANCE);

        assertThatThrownBy(() -> sender.send("+5511988887777", "Sua aula foi cancelada."))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /** Credencial errada também precisa virar falha de entrega, não outra coisa. */
    @Test
    void wrongApiKeyAlsoBecomesDeliveryException() {
        EvolutionApiSender sender = new EvolutionApiSender(
                RestClient.builder().baseUrl(baseUrl).build(), "chave-errada", INSTANCE);

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }
}
