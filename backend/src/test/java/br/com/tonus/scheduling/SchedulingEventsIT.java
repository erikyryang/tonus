package br.com.tonus.scheduling;

import br.com.tonus.scheduling.internal.SchedulingService;
import br.com.tonus.shared.TestClockConfig;
import br.com.tonus.support.PostgresContainer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que o scheduling promete ao resto do sistema (docs/eventos.md): cancelar,
 * remarcar e cadastrar feriado publicam evento. É o contrato de que a
 * notificação vai depender — o scheduling não pode chamar WhatsApp, só avisar
 * que algo aconteceu.
 *
 * Roda como {@code @ApplicationModuleTest} de propósito: sobe o scheduling e
 * suas dependências, e falha se o módulo precisar de alguém que não declarou.
 * Um {@code @SpringBootTest} inteiro esconderia exatamente esse erro.
 *
 * O seed vai por SQL porque aqui não existe camada HTTP: a fachada do
 * registration é só de consulta, por decisão de arquitetura, então não há como
 * criar aluno por ela.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ActiveProfiles("test")
class SchedulingEventsIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = PostgresContainer.INSTANCE;

    /**
     * O Clock entra por uma configuração aninhada, e não por
     * {@code @Import(TestClockConfig.class)}: o bootstrap do módulo filtra as
     * definições de bean pelos pacotes do módulo sob teste, e o TestClockConfig
     * vive em shared — seria descartado. Declarado aqui dentro, sobrevive.
     *
     * O bean é necessário para o contexto sequer subir: o AuditListener de
     * shared é entity listener de toda BaseEntity e recebe Clock no construtor,
     * então sem ele o entityManagerFactory não é criado.
     */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(TestClockConfig.FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");
    private static final LocalDate WEDNESDAY = LocalDate.parse("2026-07-29");

    @Autowired
    private SchedulingService schedulingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cancellationPublishesClassCanceled(AssertablePublishedEvents events) {
        long studentId = seedStudent();

        schedulingService.cancelClass(studentId, MONDAY);

        assertThat(events)
                .contains(ClassCanceled.class)
                .matching(ClassCanceled::studentId, studentId)
                .matching(ClassCanceled::date, MONDAY);
    }

    @Test
    void reschedulePublishesClassRescheduled(AssertablePublishedEvents events) {
        long studentId = seedStudent();

        schedulingService.rescheduleClass(studentId, MONDAY, WEDNESDAY, LocalTime.of(8, 0));

        assertThat(events)
                .contains(ClassRescheduled.class)
                .matching(ClassRescheduled::studentId, studentId)
                .matching(ClassRescheduled::originalDate, MONDAY)
                .matching(ClassRescheduled::newDate, WEDNESDAY)
                .matching(ClassRescheduled::newTime, LocalTime.of(8, 0));
    }

    @Test
    void holidayPublishesHolidayCreated(AssertablePublishedEvents events) {
        LocalDate date = LocalDate.parse("2026-12-25");

        schedulingService.createHoliday(date, "Natal");

        assertThat(events)
                .contains(HolidayCreated.class)
                .matching(HolidayCreated::date, date);
    }

    /**
     * Aluno e professor entram por SQL: as tabelas são do registration, e a
     * fachada dele não cria nada — o que é a decisão certa, não uma limitação a
     * contornar mexendo no contrato só para o teste.
     */
    private long seedStudent() {
        Long teacherId = jdbcTemplate.queryForObject("""
                INSERT INTO teacher (name, phone, active, created_at, updated_at)
                VALUES ('Professor Do Evento', '+5511999990000', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO student (name, phone, teacher_id, active, created_at, updated_at)
                VALUES ('Aluno Do Evento', '+5511988880000', ?, TRUE, now(), now())
                RETURNING id
                """, Long.class, teacherId);
    }
}
