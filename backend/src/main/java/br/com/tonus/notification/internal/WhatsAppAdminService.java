package br.com.tonus.notification.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Administration of the gateway by the ADMIN (SPEC-013).
 *
 * Holds no transaction on purpose — see {@link WhatsAppSettingsStore}. Saving
 * settings and talking to Evolution are separate steps that do not drag each
 * other: a gateway that is down must not undo a setting the ADMIN just saved,
 * and a saved setting must not be reported as applied when the gateway never
 * heard about it.
 */
@Service
public class WhatsAppAdminService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAdminService.class);

    private final WhatsAppSettingsStore store;
    private final WhatsAppSessionGateway gateway;
    private final String phoneVersionInEffect;

    WhatsAppAdminService(WhatsAppSettingsStore store,
                         WhatsAppSessionGateway gateway,
                         NotificationProperties properties) {
        this.store = store;
        this.gateway = gateway;
        this.phoneVersionInEffect = properties.evolution().phoneVersion();
    }

    public Status status() {
        return statusOf(store.read());
    }

    public List<WhatsAppSessionGateway.GatewaySession> instances() {
        return gateway.instances();
    }

    public Status updateSettings(String instance, String desiredPhoneVersion) {
        WhatsAppSettingsStore.Snapshot previous = store.read();
        WhatsAppSettingsStore.Snapshot saved = store.write(instance, desiredPhoneVersion);
        // Instance and version only — no phone, no key, nothing the gateway
        // handed back. Enough to answer "when did the studio change this?".
        log.info("whatsapp settings alteradas: instância {} -> {}, versão desejada {} -> {}",
                previous.instanceName(), saved.instanceName(),
                previous.desiredPhoneVersion(), saved.desiredPhoneVersion());
        return statusOf(saved);
    }

    /**
     * Idempotent for the connected case: an instance with a session comes back
     * with its state and no QR code, and the session is untouched.
     */
    public Session connect() {
        WhatsAppSettingsStore.Snapshot settings = store.read();
        String qrCode = gateway.connect(settings.instanceName());
        // The QR code is a pairing credential with seconds of validity. Whether
        // one was produced is worth logging; the code itself never is.
        log.info("whatsapp: pareamento solicitado para a instância {} (QR gerado: {})",
                settings.instanceName(), qrCode != null);
        return new Session(statusOf(settings), qrCode);
    }

    public Status disconnect() {
        WhatsAppSettingsStore.Snapshot settings = store.read();
        gateway.logout(settings.instanceName());
        log.info("whatsapp: sessão desconectada na instância {}", settings.instanceName());
        return statusOf(settings);
    }

    /** The instance every send resolves against — the table, not the configuration. */
    public String currentInstance() {
        return store.read().instanceName();
    }

    private Status statusOf(WhatsAppSettingsStore.Snapshot settings) {
        String instance = settings.instanceName();
        Optional<WhatsAppSessionGateway.GatewaySession> session = gateway.find(instance);
        return new Status(
                instance,
                session.map(WhatsAppSessionGateway.GatewaySession::state)
                        .orElse(SessionState.INSTANCE_MISSING),
                session.map(WhatsAppSessionGateway.GatewaySession::phone)
                        .map(WhatsAppAdminService::mask)
                        .orElse(null),
                phoneVersionInEffect,
                settings.desiredPhoneVersion());
    }

    /**
     * Enough for the ADMIN to recognize the device, and no more. Same rule that
     * already governs the send path: the full number never leaves the system.
     *
     * Anything too short to mask meaningfully collapses to {@code ****} instead
     * of leaking most of itself — a five-digit value masked "the usual way"
     * would arrive almost intact.
     */
    static String mask(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return "****";
        }
        String rest = digits.substring(4);
        return "+%s %s %s****-**%s".formatted(
                digits.substring(0, 2), digits.substring(2, 4),
                rest.charAt(0), rest.substring(rest.length() - 2));
    }

    public record Status(String instance, SessionState state, String maskedPhone,
                         String versionInEffect, String desiredVersion) {

        public boolean versionPending() {
            return !Objects.equals(versionInEffect, desiredVersion);
        }
    }

    public record Session(Status status, String qrCode) {
    }
}
