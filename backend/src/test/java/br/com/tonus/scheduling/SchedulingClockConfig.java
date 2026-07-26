package br.com.tonus.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * "Hoje" fixo para todos os testes de agenda.
 *
 * Deixou de ser opcional quando a remarcação passou a recusar data passada: com
 * o relógio do sistema, um teste que remarca para 2026-07-29 passa hoje e
 * começa a falhar sozinho quando essa data virar passado. Teste que apodrece no
 * calendário é pior que teste ausente — quebra longe de qualquer mudança de
 * código, e quem investiga procura no lugar errado.
 *
 * O instante é uma segunda-feira às 23h em São Paulo, que já é terça em UTC —
 * assim a suíte inteira também exercita o fuso do estúdio, e não só o
 * {@link CurrentDayScheduleIT}.
 */
@TestConfiguration
public class SchedulingClockConfig {

    /** Segunda-feira, 2026-07-27, 23h em São Paulo. */
    public static final Instant NOW = Instant.parse("2026-07-28T02:00:00Z");

    public static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    @Primary
    public Clock fixedStudioClock() {
        return Clock.fixed(NOW, STUDIO_ZONE);
    }
}
