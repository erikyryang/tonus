package br.com.tonus.notification.internal.domain;

/**
 * How a notice was handled: {@link #DELIVERED} means the message went out;
 * {@link #DISCARDED} means it was deliberately skipped (student inactive or
 * no contact) and the row exists only as the idempotency marker that keeps a
 * later redelivery from sending a stale notice.
 */
public enum DeliveryStatus {
    DELIVERED,
    DISCARDED
}