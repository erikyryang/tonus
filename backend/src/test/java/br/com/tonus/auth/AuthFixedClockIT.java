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
 * A token issued under a fixed clock stays valid, no matter how far from the
 * real time that clock is.
 *
 * Exists because of a real bug: issuance used the injected {@code Clock} and
 * validation used the system clock, so the token would be born expired as
 * soon as the real time passed the fixed instant plus the 10-minute TTL. The
 * symptom was {@code MyInvoicesIT.studentSeesOnlyOwnInvoices} returning 403
 * — and it passed in the morning, when the real time was still within
 * billing's clock window. A test that depends on the time of day it runs is
 * the worst kind of test: it breaks far from any code change.
 *
 * The instant here is deliberately absurd. If validation goes back to
 * looking at the system clock, this test fails every time, instead of only
 * failing after lunch.
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
