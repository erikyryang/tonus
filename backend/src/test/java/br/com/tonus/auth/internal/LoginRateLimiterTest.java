package br.com.tonus.auth.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /auth/login is public and accepts any value for {@code login} — without a
 * cap on the bucket map, an attacker with no credentials at all could
 * exhaust the heap just by varying this string on every request. This test
 * proves that the map never grows past the cap, even with far more distinct
 * keys than it can hold.
 */
class LoginRateLimiterTest {

    private static final int MAX_TRACKED_KEYS = 10_000;

    @Test
    void neverGrowsPastTheCapEvenWithManyDistinctKeys() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < MAX_TRACKED_KEYS + 500; i++) {
            limiter.tryConsume("attacker-" + i + "@tonus.com");
        }

        assertThat(limiter.trackedKeyCount())
                .as("mapa de rate-limit não pode crescer sem limite")
                .isLessThanOrEqualTo(MAX_TRACKED_KEYS);
    }

    @Test
    void stillLimitsAttemptsForTheSameKey() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        boolean sawRejection = false;
        for (int i = 0; i < 10; i++) {
            if (!limiter.tryConsume("same-user@tonus.com")) {
                sawRejection = true;
            }
        }

        assertThat(sawRejection)
                .as("o limite de tentativas por chave continua valendo depois da mudança")
                .isTrue();
    }
}
