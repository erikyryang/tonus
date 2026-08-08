package br.com.tonus.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter used when the integration is turned off: logs that a send
 * happened and talks to no one.
 *
 * It's what allows the application to start in development without a real
 * WhatsApp session. Never fails, on purpose — simulating unavailability here
 * would hide the rest of the system's happy path.
 */
class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

    LoggingWhatsAppSender() {
        // At warn level, and on startup: nobody notices the notices aren't
        // going out until a student complains they weren't told about the cancellation.
        log.warn("notification.evolution.enabled=false — nenhum WhatsApp será enviado");
    }

    @Override
    public void send(String phone, String message) {
        log.info("Envio de WhatsApp desabilitado; mensagem para {} não foi enviada", mask(phone));
    }

    /**
     * Only the last four digits. A full phone number in the log is personal
     * data leaking to a place where nobody controls retention.
     *
     * Package-visible so it has its own test: it's a privacy rule, and what
     * guarantees it shouldn't depend on someone inspecting the log by hand.
     */
    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
