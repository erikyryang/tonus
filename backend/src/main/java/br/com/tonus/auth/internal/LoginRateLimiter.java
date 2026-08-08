package br.com.tonus.auth.internal;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Limits login attempts per identifier (the provided login), not per IP —
 * isolates test scenarios/different users and prevents an account under
 * attack from consuming another account's limit.
 */
@Component
public class LoginRateLimiter {

    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Cap on the bucket map itself, not on the rate limit.
     *
     * /auth/login is public and accepts any value for {@code login} — without
     * a cap, every distinct value becomes an entry that is never removed, and
     * an attacker with no credentials at all can exhaust the instance's heap
     * just by varying this string on every request. LinkedHashMap in access
     * order (the {@code true} in the constructor) evicts the least recently
     * used entry when the map goes past the cap: whoever is actually trying
     * to log in stays within the one-minute window and is never the one
     * evicted.
     *
     * Access is synchronized on purpose: bucket4j already serializes the
     * decision inside each bucket, and the map itself only needs mutual
     * exclusion when creating or promoting an entry — not the hot path of an
     * application of this size.
     */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };

    public synchronized boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    /** Only for the cap test — nothing in production checks the map's size. */
    synchronized int trackedKeyCount() {
        return buckets.size();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
