package br.com.tonus.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.fail;

/**
 * Espera por efeito assíncrono em teste.
 *
 * {@code @ApplicationModuleListener} é assíncrono — o Modulith habilita o
 * suporte a {@code @Async} por conta própria, sem precisar de
 * {@code @EnableAsync}. Ou seja, quando a resposta HTTP volta, o listener do
 * outro módulo pode ainda não ter rodado; assertar na hora é corrida.
 *
 * Feito à mão em vez de somar Awaitility: são dez linhas e evita uma
 * dependência nova só para isso.
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
