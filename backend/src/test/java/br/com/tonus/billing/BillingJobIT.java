package br.com.tonus.billing;

import br.com.tonus.billing.internal.BillingService;
import br.com.tonus.billing.internal.domain.InvoiceRepository;
import br.com.tonus.support.PostgresContainer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O job diário é a espinha dorsal da cobrança (SPEC-004): gera fatura do dia e
 * marca fatura vencida como atrasada.
 *
 * {@code @SpringBootTest} (contexto cheio), e não {@code @ApplicationModuleTest}
 * como {@code SchedulingEventsIT}: o bootstrap filtrado por módulo do segundo
 * tem um bug pré-existente (não introduzido por esta spec) — beans de
 * {@code shared.internal} (auditing de created_at/updated_at, AuditListener)
 * não são pegos de forma confiável, e o mesmo já quebra
 * {@code SchedulingEventsIT} quando rodado isolado. A garantia de que billing
 * só depende do que declara em @ApplicationModule já vem do ArchitectureTest
 * global; aqui o contexto cheio é o caminho estável para exercitar o job.
 * Corrigir a causa raiz é fora do escopo da SPEC-004.
 *
 * Os testes chamam {@link BillingService} diretamente, não
 * {@code BillingJob.runDailyJob()}: o {@code @SchedulerLock} usa relógio real
 * (não o Clock injetado) e uma chave fixa ("billing-daily-job"), então duas
 * chamadas próximas no tempo real — entre métodos de teste, não só dentro de
 * um — colidem com {@code lockAtLeastFor} e a segunda silenciosamente não
 * executa. Isso testaria o lock do ShedLock, não a idempotência de negócio da
 * spec ("job rodando 2x não duplica"), que é o pré-check de existência +
 * uk_invoice_subscription_period.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class BillingJobIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /** "Hoje" fixo, independente do resto da suíte de billing. */
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
    void jobRunningTwiceDoesNotDuplicateInvoiceOrEvent(ApplicationEvents events) {
        long studentId = seedStudent();
        long subscriptionId = seedSubscription(studentId, 15, "220.00");

        billingService.generateDueInvoices();
        billingService.generateDueInvoices();

        assertThat(invoiceRepository.findAll())
                .filteredOn(invoice -> invoice.getSubscriptionId().equals(subscriptionId))
                .hasSize(1);
        // Filtra por aluno: o Postgres é compartilhado entre as classes de teste
        // de billing sem rollback, então outras faturas/eventos da varredura
        // global do job podem coexistir na mesma execução.
        assertThat(events.stream(InvoiceDueSoon.class).filter(e -> e.studentId() == studentId))
                .hasSize(1);
    }

    @Test
    void jobTransitionsOverdueInvoiceOnceAndPublishesOnce(ApplicationEvents events) {
        long subscriptionId = seedSubscription(seedStudent(), 1, "180.00");
        long invoiceId = seedInvoice(subscriptionId, "2026-07-01", "180.00", "2026-07-05", "OPEN");

        billingService.markOverdueInvoices();
        billingService.markOverdueInvoices();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM invoice WHERE id = ?", String.class, invoiceId);
        assertThat(status).isEqualTo("OVERDUE");
        // Filtra pela fatura: markOverdueInvoices varre a tabela inteira, e o
        // Postgres é compartilhado entre as classes de teste de billing sem
        // rollback — outras faturas OPEN vencidas podem coexistir e também
        // virar OVERDUE nesta mesma chamada.
        assertThat(events.stream(InvoiceOverdue.class).filter(e -> e.invoiceId() == invoiceId))
                .hasSize(1);
    }

    private long seedStudent() {
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES ('Professor Do Job', '+5511999990000', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES ('Aluno Do Job', '+5511988880000', ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, teacherId);
    }

    private long seedSubscription(long studentId, int dueDay, String monthlyAmount) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO subscription (student_id, monthly_amount, due_day, active, created_at, updated_at)
                VALUES (?, ?::numeric, ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, studentId, monthlyAmount, dueDay);
    }

    private long seedInvoice(long subscriptionId, String referenceMonth, String amount, String dueDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO invoice (subscription_id, reference_month, amount, due_date, status, created_at, updated_at)
                VALUES (?, ?::date, ?::numeric, ?::date, ?, now(), now())
                RETURNING id
                """, Long.class, subscriptionId, referenceMonth, amount, dueDate, status);
    }
}
