package br.com.tonus.notification.support;

import br.com.tonus.notification.internal.SessionState;
import br.com.tonus.notification.internal.WhatsAppGatewayException;
import br.com.tonus.notification.internal.WhatsAppSessionGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake for the gateway's administration side.
 *
 * Same reasoning as {@link RecordingWhatsAppSender}: the port is the only
 * place the system talks to Evolution, and replacing it is what lets the
 * endpoints be tested without a container and without a phone. The real
 * adapter has its own contract test against the running API.
 *
 * Hand-made rather than Mockito because these tests assert state transitions
 * — pairing then reading the status has to see what pairing did, and stubbing
 * each call separately would let a broken transition pass.
 */
public class RecordingWhatsAppSessionGateway implements WhatsAppSessionGateway {

    private final Map<String, GatewaySession> sessions = new LinkedHashMap<>();
    private final List<String> logouts = new ArrayList<>();
    private boolean down;

    @Override
    public List<GatewaySession> instances() {
        failIfDown();
        return List.copyOf(sessions.values());
    }

    @Override
    public Optional<GatewaySession> find(String instance) {
        failIfDown();
        return Optional.ofNullable(sessions.get(instance));
    }

    /**
     * Returns null when there is already a session: the real API has no reason
     * to hand out a QR code for a connected instance, and the endpoint's
     * idempotency depends on this distinction.
     */
    @Override
    public String connect(String instance) {
        failIfDown();
        GatewaySession existing = sessions.get(instance);
        if (existing != null && existing.state() == SessionState.CONNECTED) {
            return null;
        }
        sessions.put(instance, new GatewaySession(instance, SessionState.CONNECTING, null));
        return "data:image/png;base64,cXItY29kZS1kZS10ZXN0ZQ==";
    }

    @Override
    public void logout(String instance) {
        failIfDown();
        logouts.add(instance);
        sessions.computeIfPresent(instance,
                (name, session) -> new GatewaySession(name, SessionState.DISCONNECTED, null));
    }

    // ---- test control ----

    /** The instance exists in the gateway with a paired session. */
    public void hasConnectedInstance(String instance, String phone) {
        sessions.put(instance, new GatewaySession(instance, SessionState.CONNECTED, phone));
    }

    /** The instance exists but has no session. */
    public void hasDisconnectedInstance(String instance) {
        sessions.put(instance, new GatewaySession(instance, SessionState.DISCONNECTED, null));
    }

    /** From now on every call fails, as if Evolution were unreachable. */
    public void goDown() {
        down = true;
    }

    public Optional<SessionState> stateOf(String instance) {
        return Optional.ofNullable(sessions.get(instance)).map(GatewaySession::state);
    }

    public boolean loggedOut(String instance) {
        return logouts.contains(instance);
    }

    public void reset() {
        sessions.clear();
        logouts.clear();
        down = false;
    }

    private void failIfDown() {
        if (down) {
            throw new WhatsAppGatewayException("Evolution fora do ar (simulado no teste)", null);
        }
    }
}
