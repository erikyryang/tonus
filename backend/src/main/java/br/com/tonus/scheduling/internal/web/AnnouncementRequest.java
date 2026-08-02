package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * {@code suspendsClasses} é obrigatório e não tem default: é a diferença entre
 * "não vai ter aula" e "aviso, mas a agenda segue", e um default silencioso
 * faria um comunicado esvaziar o dia sem ninguém ter pedido.
 */
public record AnnouncementRequest(@NotNull LocalDate date,
                                  @NotBlank String description,
                                  @NotNull Boolean suspendsClasses) {
}
