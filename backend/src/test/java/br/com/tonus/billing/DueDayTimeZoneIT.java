package br.com.tonus.billing;

import br.com.tonus.registration.AbstractRegistrationIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O dia do cadastro é o do fuso do estúdio, não o de UTC.
 *
 * Tem relógio próprio — 21h30 de Brasília, quando em UTC já é o dia seguinte —
 * e por isso não herda de {@code AbstractBillingIntegrationTest}: aquele traz o
 * {@code BillingClockConfig}, e dois {@code @Primary Clock} não convivem.
 *
 * Nenhum timestamp literal aparece aqui de propósito. O instante vem do Clock da
 * própria aplicação, atravessa o JPA e volta — é o caminho real, e é ele que
 * decide o dia. Um literal em SQL testaria a convenção de gravação, que é outra
 * coisa.
 */
@Import(DueDayTimeZoneIT.LateNightClockConfig.class)
class DueDayTimeZoneIT extends AbstractRegistrationIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.tonus.billing.internal.BillingService billingService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void registrationLateAtNightCountsAsTheStudioDay() throws Exception {
        long studentId = createStudent(unique("Aluno Da Noite"), createTeacher(unique("Professor Da Noite")));

        long planId = idOf(mockMvc.perform(post("/plans")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","monthlyPrice":"200.00"}
                                """.formatted(unique("Plano Da Noite"))))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/plan-enrollments")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"planId":%d}
                                """.formatted(studentId, planId)))
                .andExpect(status().isCreated());

        billingService.generateDueInvoices();

        // Dia 27 em São Paulo; em UTC o mesmo instante já é dia 28, e o job não
        // teria faturado hoje.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT due_date FROM invoice WHERE student_id = ?", java.time.LocalDate.class, studentId))
                .isEqualTo(java.time.LocalDate.parse("2026-07-27"));
    }

    @TestConfiguration
    static class LateNightClockConfig {

        /** 2026-07-28T00:30Z = 2026-07-27, 21h30 em São Paulo. */
        static final Instant NOW = Instant.parse("2026-07-28T00:30:00Z");

        @Bean
        @Primary
        Clock lateNightStudioClock() {
            return Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
        }
    }
}
