package br.com.tonus.notification.internal.web;

import br.com.tonus.notification.internal.SessionState;
import br.com.tonus.notification.internal.WhatsAppAdminService;

/**
 * The status plus the QR code, which is null whenever there is nothing to pair.
 *
 * Null and not absent — see {@link WhatsAppStatusResponse}. The already-paired
 * case returns state CONNECTED with a null qrCode, and a front that read the
 * absent key as "has a code" would open a panel over an image that does not
 * exist.
 */
public record WhatsAppSessionResponse(String instance, SessionState state, String maskedPhone,
                                      WhatsAppStatusResponse.PhoneVersion phoneVersion,
                                      String qrCode) {

    public static WhatsAppSessionResponse from(WhatsAppAdminService.Session session) {
        WhatsAppStatusResponse status = WhatsAppStatusResponse.from(session.status());
        return new WhatsAppSessionResponse(status.instance(), status.state(), status.maskedPhone(),
                status.phoneVersion(), session.qrCode());
    }
}
