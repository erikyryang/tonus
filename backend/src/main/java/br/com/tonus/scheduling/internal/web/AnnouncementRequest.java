package br.com.tonus.scheduling.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code suspendsClasses} is mandatory and has no default: it is the difference between
 * "there will be no class" and "notice, but the schedule continues", and a silent
 * default would make an announcement empty the day without anyone having asked for it.
 */
public record AnnouncementRequest(@NotNull LocalDate date,
                                  @NotBlank @Size(max = 255) String description,
                                  @NotNull Boolean suspendsClasses) {
}
