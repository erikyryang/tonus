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
 * Não precisa ficar perto da data real. Já precisou: enquanto o
 * {@code JwtService} emitia com este Clock e validava contra o relógio do
 * sistema, um "hoje" de teste distante fazia todo token nascer expirado, e a
 * suíte passava ou falhava conforme a hora em que rodava. Hoje a validação usa
 * o mesmo Clock da emissão, e {@code AuthFixedClockIT} guarda essa
 * propriedade — a data aqui volta a ser escolha de domínio, não concessão à
 * infraestrutura de token.
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
