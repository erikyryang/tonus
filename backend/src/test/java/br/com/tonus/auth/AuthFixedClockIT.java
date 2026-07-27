package br.com.tonus.auth;

import br.com.tonus.auth.internal.domain.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token emitido sob relógio fixo continua válido, por mais longe da hora real
 * que esse relógio esteja.
 *
 * Existe por causa de um defeito real: a emissão usava o {@code Clock}
 * injetado e a validação usava o relógio do sistema, então o token nascia
 * expirado assim que a hora real passava do instante fixo mais o TTL de 10
 * minutos. O sintoma era {@code MyInvoicesIT.studentSeesOnlyOwnInvoices}
 * devolvendo 403 — e passava de manhã, quando a hora real ainda estava dentro
 * da janela do relógio de billing. Teste que depende da hora do dia em que
 * roda é o pior tipo de teste: quebra longe de qualquer mudança de código.
 *
 * O instante daqui é deliberadamente absurdo. Se a validação voltar a olhar o
 * relógio do sistema, este teste falha sempre, em vez de falhar só depois do
 * almoço.
 */
class AuthFixedClockIT extends AbstractAuthIntegrationTest {

    @TestConfiguration
    static class DistantClockConfig {

        @Bean
        @Primary
        Clock distantFixedClock() {
            return Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void tokenIssuedUnderAFixedClockIsAccepted() throws Exception {
        String login = "fixed-clock-" + UUID.randomUUID().toString().substring(0, 8) + "@tonus.com";
        createUser(login, DEFAULT_PASSWORD, Role.ADMIN);

        Cookie access = extractCookie(login(login, DEFAULT_PASSWORD), ACCESS_COOKIE);

        mockMvc.perform(get("/students").cookie(access))
                .andExpect(status().isOk());
    }
}
