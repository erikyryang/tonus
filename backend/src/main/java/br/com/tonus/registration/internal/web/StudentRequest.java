package br.com.tonus.registration.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Only the SPEC-002 fields — LGPD minimization. Extra field sent by the client
 * is discarded by Jackson (FAIL_ON_UNKNOWN_PROPERTIES turned off in shared).
 */
public record StudentRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = RegistrationValidation.PHONE_E164,
                message = RegistrationValidation.PHONE_MESSAGE) String phone,
        @NotNull Long teacherId) {
}
