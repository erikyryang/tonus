package br.com.tonus.notification.internal;

/**
 * The gateway did not answer, or answered a failure.
 *
 * Distinct from {@link WhatsAppDeliveryException}, which is about a message
 * that did not go out and has to be reprocessed. This one is about
 * administration, has no retry behind it, and becomes 502 — the distinction
 * from 500 is what separates "Evolution fell over" from "Tônus has a bug".
 */
public class WhatsAppGatewayException extends RuntimeException {

    public WhatsAppGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
