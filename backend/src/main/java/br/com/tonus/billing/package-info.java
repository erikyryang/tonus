/**
 * Billing: subscription -> invoice -> payment cycle, with a daily job.
 *
 * Depends on registration because there's no billing without a student — the
 * facade validates the student's link and state.
 *
 * The edge is acyclic: registration doesn't know about billing. A side
 * effect going outside the module is an event (InvoiceDueSoon,
 * InvoiceOverdue, PaymentRegistered), never a direct call.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.billing;
