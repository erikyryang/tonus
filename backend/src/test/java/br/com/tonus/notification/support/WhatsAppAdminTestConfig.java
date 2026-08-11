package br.com.tonus.notification.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the gateway's administration port with the recording fake.
 *
 * {@code @Primary} rather than excluding the real adapter: the production
 * condition (base-url and api-key present) is part of what the endpoints
 * decide on, and switching it off in tests would take the "not configured"
 * branch out of reach.
 */
@TestConfiguration
public class WhatsAppAdminTestConfig {

    @Bean
    @Primary
    public RecordingWhatsAppSessionGateway recordingWhatsAppSessionGateway() {
        return new RecordingWhatsAppSessionGateway();
    }
}
