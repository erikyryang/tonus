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
 * The adapter against the real Evolution API.
 *
 * {@code EvolutionApiSenderTest} uses MockRestServiceServer and answers whatever
 * we tell it to — it proves the adapter builds the request we think is right,
 * not that Evolution agrees. The difference is not theoretical: the
 * {@code number} field was going out in E.164 with "+" when the API documents
 * plain digits, and no test with a fake would catch that.
 *
 * It does not boot the Spring context: what is under test is HTTP, and booting
 * the whole application would only add time to a test that already pays for a
 * 1.75 GB container.
 *
 * What CANNOT be covered here is the successful send — it requires a WhatsApp
 * session paired by QR code, with a phone on the other side. The limit of this
 * test is the last response before that, and that is why the three cases below
 * are all failures: the value is that they are the REAL API failures, with its
 * codes and routes.
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
     * Same image and same variables as docker-compose. That is half the value
     * of this test: if the tag disappears or a variable is renamed, it breaks
     * here and not in the deploy. That is how it was discovered that
     * {@code atendai/evolution-api} — the old product repository — no longer
     * even exists for pull.
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
                    // Not the compose value: here it's Testcontainers that maps
                    // the port, and nothing renders the Manager UI.
                    .withEnv("SERVER_URL", "http://localhost:8080")
                    .withEnv("CONFIG_SESSION_PHONE_VERSION", "2.3000.1043857760")
                    .withEnv("CONFIG_SESSION_PHONE_CLIENT", "Tonus")
                    .withEnv("CONFIG_SESSION_PHONE_NAME", "Chrome")
                    .withEnv("QRCODE_LIMIT", "30")
                    .withEnv("LANGUAGE", "pt-BR")
                    .withEnv("DEL_INSTANCE", "false")
                    .withEnv("WEBHOOK_GLOBAL_ENABLED", "false")
                    .withEnv("TELEMETRY_ENABLED", "false")
                    .withEnv("DATABASE_SAVE_DATA_INSTANCE", "true")
                    .withEnv("DATABASE_SAVE_DATA_NEW_MESSAGE", "false")
                    .withEnv("DATABASE_SAVE_MESSAGE_UPDATE", "false")
                    .withEnv("DATABASE_SAVE_DATA_CONTACTS", "false")
                    .withEnv("DATABASE_SAVE_DATA_CHATS", "false")
                    .withEnv("DATABASE_SAVE_DATA_HISTORIC", "false")
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

        // The instance is born with status "close": it exists in the database,
        // without a WhatsApp session. That is the closest to production this can
        // get without pairing a phone.
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
     * The route the **adapter** builds is the one Evolution serves.
     *
     * The assertion is about the response body, not the status: a wrong path
     * would also give 404, only from routing. This 404 comes from inside the
     * sendText handler complaining about the instance — proof that the URI, the
     * header and the body built by the adapter reached the right place.
     *
     * Points at a nonexistent instance on purpose: it is the only way to make
     * the API answer something specific to the route without a paired session.
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

    /** {@code apikey} really is the authentication mechanism, and in the header. */
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
     * The production adapter, against the real server.
     *
     * Without a WhatsApp session Evolution returns 500, and what has to hold is
     * that this becomes {@link WhatsAppDeliveryException} — it is what the
     * record rollback and the reprocessing depend on. An error escaping in
     * another type would pass as a programming failure and the message would be
     * lost.
     */
    @Test
    void adapterTurnsARealFailureIntoDeliveryException() {
        EvolutionApiSender sender = new EvolutionApiSender(
                RestClient.builder().baseUrl(baseUrl).build(), API_KEY, INSTANCE);

        assertThatThrownBy(() -> sender.send("+5511988887777", "Sua aula foi cancelada."))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }

    /** Wrong credentials also have to become a delivery failure, not something else. */
    @Test
    void wrongApiKeyAlsoBecomesDeliveryException() {
        EvolutionApiSender sender = new EvolutionApiSender(
                RestClient.builder().baseUrl(baseUrl).build(), "chave-errada", INSTANCE);

        assertThatThrownBy(() -> sender.send("+5511988887777", "Oi"))
                .isInstanceOf(WhatsAppDeliveryException.class);
    }
}
