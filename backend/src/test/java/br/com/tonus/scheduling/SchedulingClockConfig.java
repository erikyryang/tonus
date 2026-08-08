package br.com.tonus.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A fixed "today" for all schedule tests.
 *
 * It stopped being optional when rescheduling started rejecting past dates:
 * with the system clock, a test that reschedules to 2026-07-29 passes today
 * and starts failing on its own when that date becomes the past. A test that
 * rots on the calendar is worse than a missing test — it breaks far from any
 * code change, and whoever investigates looks in the wrong place.
 *
 * The instant is a Monday at 23h in São Paulo, which is already Tuesday in
 * UTC — so the whole suite also exercises the studio's timezone, not just
 * {@link CurrentDayScheduleIT}.
 */
@TestConfiguration
public class SchedulingClockConfig {

    /** Monday, 2026-07-27, 23h in São Paulo. */
    public static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");

    public static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    @Primary
    public Clock fixedStudioClock() {
        return Clock.fixed(NOW, STUDIO_ZONE);
    }
}
