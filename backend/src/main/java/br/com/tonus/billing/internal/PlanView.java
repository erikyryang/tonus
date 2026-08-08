package br.com.tonus.billing.internal;

import br.com.tonus.billing.internal.domain.Plan;

/**
 * Plan paired with what the screen needs and the `plan` row doesn't hold:
 * how many students lose the charge if it's deactivated.
 *
 * Same reason {@link InvoiceView} exists — the count lives in another table,
 * and resolving it on the client would cost loading the whole studio's
 * enrollments.
 */
public record PlanView(Plan plan, long activeEnrollments) {
}
