package br.com.tonus.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Single Postgres container for the whole test JVM ("singleton container"
 * pattern): started once in the static initializer, never stopped explicitly —
 * Ryuk tears it down at the end of the JVM.
 *
 * It lives here, and not inside a test base class, because there is more than
 * one entry point: the integration tests over HTTP and the
 * {@code @ApplicationModuleTest}, which do not share a hierarchy. Each one
 * declaring its own would start one container per bootstrap.
 *
 * Using {@code @Testcontainers}/{@code @Container} here would restart the
 * container for every class and leave Spring with a cached ApplicationContext
 * pointing at a dead port.
 */
public final class PostgresContainer {

    /**
     * {@code max_connections} goes up because the bottleneck is the server, not
     * the pools. Spring caches one ApplicationContext per test configuration
     * combination, and each one opens its own Hikari of 10 connections — with
     * Postgres' default of 100, the tenth context to boot fails with "sorry,
     * too many clients already", failing in some random class of the suite,
     * with no relation to the one that added the context.
     *
     * Tightening the pool instead would be worse: the Modulith async listeners
     * grab connections in parallel with the test thread, and a short pool
     * trades the boot error for a connection timeout in the middle of the test.
     */
    public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16")
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
