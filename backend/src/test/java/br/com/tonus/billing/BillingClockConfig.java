package br.com.tonus.billing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * "Hoje" fixo para a suíte MockMvc de billing.
 *
 * Precisa ficar perto da data real: o login gera um JWT cujo {@code iat} usa
 * este Clock, mas a validação do token (JJWT) compara {@code exp} contra o
 * relógio real do sistema, não contra este bean. Um "hoje" de teste distante
 * da data real faria todo token nascer expirado (ou nunca expirar) na
 * validação — mesmo motivo por trás da escolha de data em
 * {@code SchedulingClockConfig}.
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
