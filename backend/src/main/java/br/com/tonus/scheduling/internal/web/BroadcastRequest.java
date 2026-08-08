package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Limit of 500 characters: free-form message is not the same thing as unlimited. */
public record BroadcastRequest(@NotBlank @Size(max = 500) String message) {
}
