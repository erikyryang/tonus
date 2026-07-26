package br.com.tonus.shared.internal;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * O fuso é o do estúdio, não UTC.
     *
     * Instante não depende de fuso, mas "hoje" depende: os endpoints /me
     * resolvem a data corrente por {@code LocalDate.now(clock)}, e com um clock
     * em UTC todo aluno que abrisse a agenda depois das 21h de Brasília veria a
     * aula do dia seguinte. O erro só aparece de noite, que é o pior tipo de
     * erro para se descobrir em produção.
     */
    private static final ZoneId STUDIO_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(STUDIO_ZONE);
    }
}
