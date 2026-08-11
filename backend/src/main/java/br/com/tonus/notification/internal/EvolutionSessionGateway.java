package br.com.tonus.notification.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Evolution API adapter for the administration side.
 *
 * Routes and field names checked against the running v2.3.7 image — the
 * contract test is what keeps them honest when a tag moves.
 *
 * Deserialized into records rather than {@code Map}: Evolution returns around
 * twenty fields per instance and only three matter here. The records name those
 * three, so a rename upstream fails at the boundary instead of quietly becoming
 * a null that reads as "disconnected".
 */
class EvolutionSessionGateway implements WhatsAppSessionGateway {

    private static final ParameterizedTypeReference<List<InstancePayload>> INSTANCE_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String apiKey;

    EvolutionSessionGateway(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<GatewaySession> instances() {
        List<InstancePayload> payloads = call(() -> restClient.get()
                .uri("/instance/fetchInstances")
                .header("apikey", apiKey)
                .retrieve()
                .body(INSTANCE_LIST));

        return payloads == null ? List.of() : payloads.stream().map(InstancePayload::toSession).toList();
    }

    @Override
    public Optional<GatewaySession> find(String instance) {
        return instances().stream().filter(session -> session.name().equals(instance)).findFirst();
    }

    /**
     * Creates the instance when it is missing, and asks for a QR code otherwise.
     * Returns null when a session already exists — that is what makes the
     * endpoint idempotent instead of destructive.
     */
    @Override
    public String connect(String instance) {
        Optional<GatewaySession> existing = find(instance);
        if (existing.isPresent() && existing.get().state() == SessionState.CONNECTED) {
            return null;
        }
        if (existing.isEmpty()) {
            return qrCodeOf(call(() -> restClient.post()
                    .uri("/instance/create")
                    .header("apikey", apiKey)
                    .body(Map.of("instanceName", instance,
                            "integration", "WHATSAPP-BAILEYS",
                            "qrcode", true))
                    .retrieve()
                    .body(ConnectPayload.class)));
        }
        return qrCodeOf(call(() -> restClient.get()
                .uri("/instance/connect/{instance}", instance)
                .header("apikey", apiKey)
                .retrieve()
                .body(ConnectPayload.class)));
    }

    @Override
    public void logout(String instance) {
        call(() -> restClient.delete()
                .uri("/instance/logout/{instance}", instance)
                .header("apikey", apiKey)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * {@code /instance/create} nests the code under {@code qrcode};
     * {@code /instance/connect} returns it at the top level. Both shapes are
     * real, and the record carries both rather than the caller guessing.
     */
    private static String qrCodeOf(ConnectPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload.qrcode() != null && payload.qrcode().base64() != null) {
            return payload.qrcode().base64();
        }
        return payload.base64();
    }

    /**
     * Every failure becomes {@link WhatsAppGatewayException}. Without the
     * translation, an Evolution that is down would surface as 500 and read as a
     * bug in Tônus.
     */
    private <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RestClientException e) {
            throw new WhatsAppGatewayException("Evolution não respondeu à operação de sessão", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InstancePayload(String name, String connectionStatus, String ownerJid) {

        GatewaySession toSession() {
            return new GatewaySession(name, stateOf(connectionStatus), ownerJid);
        }

        /**
         * Anything that is not "open" or "connecting" is treated as no session.
         * Evolution uses "close" today; a value nobody anticipated should read
         * as disconnected, which is the state that offers pairing.
         */
        private static SessionState stateOf(String connectionStatus) {
            if (connectionStatus == null) {
                return SessionState.DISCONNECTED;
            }
            return switch (connectionStatus) {
                case "open" -> SessionState.CONNECTED;
                case "connecting" -> SessionState.CONNECTING;
                default -> SessionState.DISCONNECTED;
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConnectPayload(QrCode qrcode, String base64) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record QrCode(String base64) {
        }
    }
}
