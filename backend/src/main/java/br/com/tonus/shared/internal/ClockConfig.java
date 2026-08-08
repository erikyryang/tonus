package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * The timezone is the studio's, not UTC.
     *
     * An instant doesn't depend on timezone, but "today" does: the /me
     * endpoints resolve the current date via {@code LocalDate.now(clock)},
     * and with a clock in UTC every student who opened the schedule after 9pm
     * Brasília time would see the next day's class. The bug only shows up at
     * night, which is the worst kind of bug to discover in production.
     */
    private static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(STUDIO_ZONE);
    }
}
