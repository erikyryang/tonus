package br.com.tonus.notification.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O texto que chega ao aluno.
 *
 * Vale teste próprio porque é a única parte do módulo que uma pessoa lê: data
 * em formato ISO ou valor com ponto decimal não quebram nada e passariam
 * despercebidos em qualquer teste de integração, mas quem recebe a mensagem
 * nota na hora.
 */
class MessageTemplateTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-07-27");

    @Test
    void datesAreBrazilianNotIso() {
        assertThat(MessageTemplate.date(MONDAY)).isEqualTo("27/07/2026");
    }

    @Test
    void timesDropSecondsAndKeepLeadingZero() {
        assertThat(MessageTemplate.time(LocalTime.of(8, 0))).isEqualTo("08:00");
        assertThat(MessageTemplate.time(LocalTime.of(14, 30))).isEqualTo("14:30");
    }

    @Test
    void moneyUsesCommaAndAlwaysTwoDecimals() {
        assertThat(MessageTemplate.money(new BigDecimal("250"))).isEqualTo("R$ 250,00");
        assertThat(MessageTemplate.money(new BigDecimal("250.5"))).isEqualTo("R$ 250,50");
        assertThat(MessageTemplate.money(new BigDecimal("1234.56"))).isEqualTo("R$ 1234,56");
    }

    @Test
    void everyTemplateOpensWithTheStudentName() {
        for (MessageTemplate template : MessageTemplate.values()) {
            String rendered = template.render("Fulano", "a", "b", "c");
            assertThat(rendered).as("template %s", template).startsWith("Olá, Fulano!");
        }
    }

    @Test
    void cancellationSaysWhichDay() {
        assertThat(MessageTemplate.CLASS_CANCELED.render("Ana", MessageTemplate.date(MONDAY)))
                .isEqualTo("Olá, Ana! Sua aula do dia 27/07/2026 foi cancelada.");
    }

    @Test
    void rescheduleSaysFromWhereToWhere() {
        String rendered = MessageTemplate.CLASS_RESCHEDULED.render("Ana",
                MessageTemplate.date(MONDAY),
                MessageTemplate.date(LocalDate.parse("2026-07-29")),
                MessageTemplate.time(LocalTime.of(8, 0)));

        assertThat(rendered).isEqualTo(
                "Olá, Ana! Sua aula do dia 27/07/2026 foi remarcada para 29/07/2026 às 08:00.");
    }

    @Test
    void paymentConfirmsAmountAndDate() {
        String rendered = MessageTemplate.PAYMENT_REGISTERED.render("Ana",
                MessageTemplate.money(new BigDecimal("250.00")), MessageTemplate.date(MONDAY));

        assertThat(rendered).isEqualTo(
                "Olá, Ana! Recebemos seu pagamento de R$ 250,00 em 27/07/2026. Obrigado!");
    }
}
