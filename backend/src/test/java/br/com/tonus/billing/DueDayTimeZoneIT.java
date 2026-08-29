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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The registration day is the one in the studio's timezone, not UTC.
 *
 * It has its own clock — 21:30 in Brasília, when it is already the next day in
 * UTC — and for that reason it does not extend
 * {@code AbstractBillingIntegrationTest}: that one brings the
 * {@code BillingClockConfig}, and two {@code @Primary Clock} beans do not
 * coexist.
 *
 * No literal timestamp appears here on purpose. The instant comes from the
 * application's own Clock, crosses JPA and comes back — that is the real path,
 * and it is what decides the day. A literal in SQL would test the write
 * convention, which is a different thing.
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","monthlyPrice":"200.00"}
                                """.formatted(unique("Plano Da Noite"))))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/plan-enrollments")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"planId":%d}
                                """.formatted(studentId, planId)))
                .andExpect(status().isCreated());

        billingService.generateDueInvoices();

        // Day 27 in São Paulo; in UTC the same instant is already day 28, and the
        // job would not have billed today.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT due_date FROM invoice WHERE student_id = ?", java.time.LocalDate.class, studentId))
                .isEqualTo(java.time.LocalDate.parse("2026-07-27"));
    }

    @TestConfiguration
    static class LateNightClockConfig {

        /** 2026-07-28T00:30Z = 2026-07-27, 21:30 in São Paulo. */
        static final Instant NOW = Instant.parse("2026-07-28T00:30:00Z");

        @Bean
        @Primary
        Clock lateNightStudioClock() {
            return Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
        }
    }
}
