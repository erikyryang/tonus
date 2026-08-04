package br.com.tonus.auth.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /auth/login é público e aceita qualquer valor de {@code login} — sem teto no
 * mapa de buckets, um atacante sem credencial nenhuma esgotaria a heap só
 * variando essa string a cada requisição. Este teste prova que o mapa nunca
 * cresce além do teto, mesmo com muito mais chaves distintas do que ele
 * comporta.
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
