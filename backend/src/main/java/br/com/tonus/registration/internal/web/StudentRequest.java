package br.com.tonus.registration.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Só os campos da SPEC-002 — minimização LGPD. Campo extra enviado pelo cliente
 * é descartado pelo Jackson (FAIL_ON_UNKNOWN_PROPERTIES desligado no shared).
 */
public record StudentRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = RegistrationValidation.PHONE_E164,
                message = RegistrationValidation.PHONE_MESSAGE) String phone,
        @NotNull Long teacherId) {
}
