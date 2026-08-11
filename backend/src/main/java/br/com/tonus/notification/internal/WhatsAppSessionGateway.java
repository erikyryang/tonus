package br.com.tonus.notification.internal;

import java.util.List;
import java.util.Optional;

/**
 * The gateway's administration side (SPEC-013).
 *
 * Separate from {@link WhatsAppSender} on purpose: sending has a legitimate
 * fake for development ({@link LoggingWhatsAppSender}) and administering a
 * session does not — "pairing into the void" means nothing. It is also why
 * this port does NOT depend on {@code notification.evolution.enabled}: turning
 * sending on is what one does *after* pairing, so a pairing screen gated on
 * that flag would leave no possible order of operations.
 */
public interface WhatsAppSessionGateway {

    List<GatewaySession> instances();

    Optional<GatewaySession> find(String instance);

    /**
     * Creates the instance if it does not exist and starts pairing.
     *
     * @return the QR code, or {@code null} when there is already a session —
     *         re-pairing by mistake must not cost the session that works.
     */
    String connect(String instance);

    void logout(String instance);

    record GatewaySession(String name, SessionState state, String phone) {
    }
}
