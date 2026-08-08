package br.com.tonus.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.fail;

/**
 * Waits for an asynchronous effect in a test.
 *
 * {@code @ApplicationModuleListener} is asynchronous — Modulith enables the
 * {@code @Async} support on its own, without needing {@code @EnableAsync}.
 * That is, when the HTTP response returns, the listener in the other module
 * may not have run yet; asserting right away is a race.
 *
 * Hand-rolled instead of adding Awaitility: it is ten lines and avoids a new
 * dependency just for that.
 */
public final class Eventually {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 25;

    private Eventually() {
    }

    public static void eventually(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido esperando: " + description, e);
            }
        }
        fail("Condição não ocorreu em %s: %s".formatted(TIMEOUT, description));
    }
}
