package br.com.tonus.registration.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeacherRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = RegistrationValidation.PHONE_E164,
                message = RegistrationValidation.PHONE_MESSAGE) String phone) {
}
