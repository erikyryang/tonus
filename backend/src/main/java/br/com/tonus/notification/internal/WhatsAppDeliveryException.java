package br.com.tonus.notification.internal;

/**
 * The message didn't go out.
 *
 * It's a runtime exception to roll back the listener's transaction: the
 * record in notification_log is undone and the publication is left with a
 * null completion_date, which is what Modulith's registry reprocesses
 * later. Handling the failure instead of propagating it would mark the
 * event as delivered without anyone having been notified.
 */
public class WhatsAppDeliveryException extends RuntimeException {

    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
