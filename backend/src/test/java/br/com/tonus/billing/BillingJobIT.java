package br.com.tonus.billing;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.billing.internal.domain.InvoiceRepository;
import br.com.tonus.support.PostgresContainer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O job diário é a espinha dorsal da cobrança (SPEC-004): gera fatura do dia e
 * marca fatura vencida como atrasada. Roda como {@code @ApplicationModuleTest}
 * de propósito, mesmo padrão de {@code SchedulingEventsIT} — sobe o billing e
 * suas dependências, e falha se o módulo precisar de alguém não declarado.
 *
 * Os testes chamam {@link BillingService} diretamente, não
 * {@code BillingJob.runDailyJob()}: o {@code @SchedulerLock} usa relógio real
 * (não o Clock injetado) e uma chave fixa ("billing-daily-job"), então duas
 * chamadas próximas no tempo real — entre métodos de teste, não só dentro de
 * um — colidem com {@code lockAtLeastFor} e a segunda silenciosamente não
 * executa. Isso testaria o lock do ShedLock, não a idempotência de negócio da
 * spec ("job rodando 2x não duplica"), que é o pré-check de existência +
 * uk_invoice_student_period.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
class BillingJobIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /**
     * Configuração aninhada, não {@code @Import}: o bootstrap do módulo filtra
     * as definições de bean pelos pacotes do módulo sob teste, mesmo motivo
     * documentado em {@code SchedulingEventsIT.FixedClockConfig}.
     */
    @TestConfiguration
    static class FixedClockConfig {

        static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BillingService billingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void jobRunningTwiceDoesNotDuplicateInvoiceOrEvent(AssertablePublishedEvents events) {
        // Cadastrado num dia 15: é o vencimento dele, e é "hoje" para o relógio
        // fixo desta classe — sem isso o job não teria o que faturar.
        long studentId = seedStudent("2026-01-15 10:00:00");
        seedEnrollment(studentId, seedPlan("Plano Do Job", "220.00"));

        billingService.generateDueInvoices();
        billingService.generateDueInvoices();

        assertThat(invoiceRepository.findAll())
                .filteredOn(invoice -> invoice.getStudentId().equals(studentId))
                .hasSize(1);
        // Filtra por aluno: o Postgres é compartilhado entre as classes de teste
        // de billing sem rollback, então outras faturas/eventos da varredura
        // global do job podem coexistir na mesma execução.
        assertThat(events.ofType(InvoiceDueSoon.class).matchingValue(InvoiceDueSoon::studentId, studentId))
                .hasSize(1);
    }

    @Test
    void jobTransitionsOverdueInvoiceOnceAndPublishesOnce(AssertablePublishedEvents events) {
        long invoiceId = seedInvoice(seedStudent("2026-01-01 10:00:00"),
                "2026-07-01", "180.00", "2026-07-05", "OPEN");

        billingService.markOverdueInvoices();
        billingService.markOverdueInvoices();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM invoice WHERE id = ?", String.class, invoiceId);
        assertThat(status).isEqualTo("OVERDUE");
        // Filtra pela fatura: markOverdueInvoices varre a tabela inteira, e o
        // Postgres é compartilhado entre as classes de teste de billing sem
        // rollback — outras faturas OPEN vencidas podem coexistir e também
        // virar OVERDUE nesta mesma chamada.
        assertThat(events.ofType(InvoiceOverdue.class).matchingValue(InvoiceOverdue::invoiceId, invoiceId))
                .hasSize(1);
    }

    private long seedStudent(String createdAt) {
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES ('Professor Do Job', '+5511999990000', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES ('Aluno Do Job', '+5511988880000', ?, TRUE, ?::timestamp, now())
                RETURNING id
                """, Long.class, teacherId, createdAt);
    }

    private long seedPlan(String name, String monthlyPrice) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO plan (name, monthly_price, active, created_at, updated_at)
                VALUES (?, ?::numeric, TRUE, now(), now())
                RETURNING id
                """, Long.class, name, monthlyPrice);
    }

    private void seedEnrollment(long studentId, long planId) {
        jdbcTemplate.update("""
                INSERT INTO plan_enrollment (student_id, plan_id, active, created_at, updated_at)
                VALUES (?, ?, TRUE, now(), now())
                """, studentId, planId);
    }

    private long seedInvoice(long studentId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (student_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, studentId, referenceMonth, amount, dueDate, status);
    }
}
