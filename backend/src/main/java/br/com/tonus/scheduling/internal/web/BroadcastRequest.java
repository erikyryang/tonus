package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Limite de 500 caracteres: mensagem livre não é a mesma coisa que ilimitada. */
public record BroadcastRequest(@NotBlank @Size(max = 500) String message) {
}
