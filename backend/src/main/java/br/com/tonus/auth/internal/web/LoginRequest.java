package br.com.tonus.auth.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String login,
        @NotBlank @Size(max = 255) String password) {
}
