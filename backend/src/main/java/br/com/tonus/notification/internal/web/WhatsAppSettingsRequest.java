package br.com.tonus.notification.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The version is validated here because an invalid one would otherwise only
 * show itself at the next pairing, long after being saved — and the symptom
 * there is a QR code that never connects, with nothing pointing back to this
 * field.
 *
 * The lengths mirror the columns in V025. Without them the database refuses the
 * value and the ADMIN gets a 500 for what is an ordinary typing mistake: the
 * constraint is the same, the difference is whether it is explained.
 */
public record WhatsAppSettingsRequest(
        @NotBlank
        @Size(max = 100, message = "nome da instância deve ter no máximo 100 caracteres")
        String instance,

        @NotBlank
        @Size(max = 40, message = "versão do WhatsApp deve ter no máximo 40 caracteres")
        @Pattern(regexp = "2\\.\\d+\\.\\d+",
                message = "versão do WhatsApp deve ter o formato 2.<major>.<build>")
        String desiredPhoneVersion) {
}
