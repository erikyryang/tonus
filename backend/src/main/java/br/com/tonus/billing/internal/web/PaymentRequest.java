package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payment body: only the date the money came in. Who registered it comes
 * from the authenticated principal, never from the client.
 *
 * The date is required, not "today by default": this endpoint fires a
 * WhatsApp receipt and moves the subscription's due date, and neither of
 * these two things has an undo. A forgotten body has to become a 400, not a
 * payment logged on a date nobody chose.
 *
 * The rejection of a future date lives in BillingService, not in {@code
 * @PastOrPresent}: Bean Validation compares against the system clock, and
 * "today" in this project only comes from the injected Clock.
 */
public record PaymentRequest(@NotNull LocalDate paymentDate) {
}
