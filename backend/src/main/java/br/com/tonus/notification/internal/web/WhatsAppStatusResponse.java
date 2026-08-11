package br.com.tonus.notification.internal.web;

import br.com.tonus.notification.internal.SessionState;
import br.com.tonus.notification.internal.WhatsAppAdminService;

/**
 * The null fields are emitted, not omitted: "there is no paired phone" has to
 * arrive as {@code null} and not as an absent key. A consumer checking
 * {@code !== null} reads an absent key as a value — which, on qrCode below, is
 * the difference between showing a QR panel and not showing one.
 */
public record WhatsAppStatusResponse(String instance, SessionState state, String maskedPhone,
                                     PhoneVersion phoneVersion) {

    public static WhatsAppStatusResponse from(WhatsAppAdminService.Status status) {
        return new WhatsAppStatusResponse(
                status.instance(), status.state(), status.maskedPhone(),
                new PhoneVersion(status.versionInEffect(), status.desiredVersion(),
                        status.versionPending()));
    }

    public record PhoneVersion(String inEffect, String desired, boolean pending) {
    }
}
