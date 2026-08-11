package br.com.tonus.notification.internal;

/**
 * State of the WhatsApp session on the gateway.
 *
 * {@code INSTANCE_MISSING} is never produced by the gateway — absence there is
 * an empty {@link java.util.Optional}. It exists because the state the ADMIN
 * needs to see includes "configuration points at an instance that was never
 * created", and that is a state of the system, not an error.
 */
public enum SessionState {
    INSTANCE_MISSING,
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
