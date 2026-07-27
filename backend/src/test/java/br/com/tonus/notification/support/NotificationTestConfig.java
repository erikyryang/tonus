package br.com.tonus.notification.support;

import br.com.tonus.shared.TestClockConfig;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Beans de teste do módulo notification.
 *
 * Mora em {@code br.com.tonus.notification.support}, e não em shared, porque o
 * bootstrap do {@code @ApplicationModuleTest} filtra as definições de bean
 * pelos pacotes do módulo sob teste — o TestClockConfig de shared seria
 * descartado. Dentro do pacote do módulo, sobrevive ao filtro.
 *
 * O Clock é necessário para o contexto sequer subir: o AuditListener de shared
 * é entity listener de toda BaseEntity e recebe Clock no construtor.
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
