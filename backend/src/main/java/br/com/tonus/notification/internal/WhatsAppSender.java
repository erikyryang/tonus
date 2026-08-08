package br.com.tonus.notification.internal;

/**
 * Outbound port for WhatsApp. Switching providers means writing another
 * adapter; the listeners don't change.
 *
 * Stays in internal/, not at the module root: the root is the contract for
 * other modules, and no module calls notification — it only listens.
 * Exposing the port out there would invite exactly what the project
 * forbids, which is another module sending WhatsApp directly instead of
 * publishing an event.
 */
public interface WhatsAppSender {

    /**
     * Delivers the message, or raises {@link WhatsAppDeliveryException}. It
     * has no success/failure return value on purpose: the caller needs the
     * failure to roll back the transaction, and a return value ignored by
     * mistake is silence where there should be reprocessing.
     */
    void send(String phone, String message);
}
