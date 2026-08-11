package br.com.tonus.notification.internal;

/**
 * There is no base-url or api-key: the application cannot talk to any gateway.
 *
 * Becomes 503 and not 500 — the deployment is incomplete, which is the only
 * condition where this API admits it cannot answer. Nothing the ADMIN clicks
 * on the screen would help, and the screen says so instead of offering
 * actions.
 */
public class WhatsAppGatewayNotConfiguredException extends RuntimeException {

    public WhatsAppGatewayNotConfiguredException(String message) {
        super(message);
    }
}
