package br.com.tonus.notification.support;

import br.com.tonus.shared.TestClockConfig;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test beans for the notification module.
 *
 * Lives in {@code br.com.tonus.notification.support}, not in shared, because
 * the {@code @ApplicationModuleTest} bootstrap filters the bean definitions by
 * the packages of the module under test — the shared TestClockConfig would be
 * discarded. Inside the module package, it survives the filter.
 *
 * The Clock is needed for the context to even boot: the shared AuditListener
 * is an entity listener for every BaseEntity and receives Clock in the
 * constructor.
 */
@TestConfiguration
public class NotificationTestConfig {

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(TestClockConfig.FIXED_INSTANT, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    public RecordingWhatsAppSender recordingWhatsAppSender() {
        return new RecordingWhatsAppSender();
    }
}
