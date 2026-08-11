package br.com.tonus.notification.internal;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The administration adapter against the real Evolution API (SPEC-013).
 *
 * {@code WhatsAppAdminIT} runs against a fake and proves the endpoints and the
 * state machine. What it cannot prove is that Evolution agrees about field
 * names — {@code connectionStatus}, {@code ownerJid}, {@code qrcode.base64} —
 * and those were read off a running instance, not off documentation. A rename
 * there breaks here, not in the studio's face.
 *
 * Same image and variables as docker-compose, for the same reason as
 * {@code EvolutionApiContractIT}.
 *
 * What CANNOT be covered is a successful pairing: it needs a phone scanning the
 * QR code. The limit is the QR code being produced, which is the last step
 * before the human one.
 */
class EvolutionSessionGatewayContractIT {

    private static final String API_KEY = "chave-de-contrato-de-sessao";

    private static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> EVOLUTION_DB =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("evolution")
                    .withUsername("tonus")
                    .withPassword("tonus")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("db");

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

    private static EvolutionSessionGateway gateway;

    @BeforeAll
    static void startEvolution() {
        EVOLUTION.start();
        String baseUrl = "http://%s:%d".formatted(EVOLUTION.getHost(), EVOLUTION.getMappedPort(8080));
        gateway = new EvolutionSessionGateway(
                RestClient.builder().baseUrl(baseUrl).build(), API_KEY);
    }

    /** Configuration pointing at an instance nobody created — the real case in the environment today. */
    @Test
    void missingInstanceIsAnEmptyOptionalAndNotAFailure() {
        assertThat(gateway.find("instancia-que-nunca-existiu")).isEmpty();
    }

    /**
     * Creating and reading back is what pins the field names down: the state
     * comes from {@code connectionStatus} and the name from {@code name}. A
     * rename on either side would leave every instance reading DISCONNECTED,
     * which is the kind of wrong that looks plausible.
     */
    @Test
    void createsTheInstanceAndReturnsAQrCode() {
        String instance = uniqueInstance();

        String qrCode = gateway.connect(instance);

        assertThat(qrCode).isNotBlank();
        Optional<WhatsAppSessionGateway.GatewaySession> session = gateway.find(instance);
        assertThat(session).isPresent();
        assertThat(session.get().name()).isEqualTo(instance);
        assertThat(session.get().state())
                .isIn(SessionState.CONNECTING, SessionState.DISCONNECTED);
        assertThat(gateway.instances()).anyMatch(each -> each.name().equals(instance));
    }

    /** Logging out is not deleting — the instance has to survive so it can be paired again. */
    @Test
    void logoutKeepsTheInstance() {
        String instance = uniqueInstance();
        gateway.connect(instance);

        gateway.logout(instance);

        assertThat(gateway.find(instance)).isPresent();
    }

    /**
     * A wrong key becomes {@link WhatsAppGatewayException} and not something
     * else — it is what the 502 in the endpoint depends on. Escaping in another
     * type would surface as 500 and read as a bug in Tônus.
     */
    @Test
    void wrongApiKeyBecomesGatewayException() {
        EvolutionSessionGateway wrongKey = new EvolutionSessionGateway(
                RestClient.builder()
                        .baseUrl("http://%s:%d".formatted(EVOLUTION.getHost(), EVOLUTION.getMappedPort(8080)))
                        .build(),
                "chave-errada");

        assertThatThrownBy(wrongKey::instances).isInstanceOf(WhatsAppGatewayException.class);
    }

    /** Evolution unreachable is an IOException inside RestClient, not an HTTP status. */
    @Test
    void unreachableGatewayBecomesGatewayException() {
        EvolutionSessionGateway offline = new EvolutionSessionGateway(
                RestClient.builder().baseUrl("http://localhost:1").build(), API_KEY);

        assertThatThrownBy(offline::instances).isInstanceOf(WhatsAppGatewayException.class);
    }

    /** The container is shared by the class; a name per test keeps one from seeing another's. */
    private static String uniqueInstance() {
        return "contrato-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
