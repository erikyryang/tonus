package br.com.tonus.billing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Fixed "today" for billing's MockMvc suite.
 *
 * It doesn't need to be close to the real date. It once did: while {@code
 * JwtService} issued with this Clock and validated against the system
 * clock, a distant test "today" made every token born expired, and the
 * suite passed or failed depending on the hour it ran. Today validation uses
 * the same Clock as issuance, and {@code AuthFixedClockIT} guards that
 * property — the date here goes back to being a domain choice, not a
 * concession to the token infrastructure.
 */
@TestConfiguration
public class BillingClockConfig {

    public static final Instant NOW = Instant.parse("2026-07-27T13:00:00Z");

    public static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    @Primary
    public Clock fixedStudioClock() {
        return Clock.fixed(NOW, STUDIO_ZONE);
    }
}
