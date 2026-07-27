package br.com.tonus.notification.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A máscara de telefone.
 *
 * É regra de privacidade do projeto — "nunca logar telefone completo" — e por
 * isso tem teste direto em vez de depender de alguém ler o log e reparar. Os
 * casos degenerados existem porque é justamente neles que uma máscara ingênua
 * estoura ou deixa passar o número inteiro.
 */
class LoggingWhatsAppSenderTest {

    @Test
    void keepsOnlyTheLastFourDigits() {
        assertThat(LoggingWhatsAppSender.mask("+5511988887777")).isEqualTo("****7777");
    }

    @Test
    void numberShorterThanTheMaskIsHiddenEntirely() {
        assertThat(LoggingWhatsAppSender.mask("7777")).isEqualTo("****");
        assertThat(LoggingWhatsAppSender.mask("77")).isEqualTo("****");
        assertThat(LoggingWhatsAppSender.mask("")).isEqualTo("****");
    }

    @Test
    void nullIsHiddenInsteadOfBlowingUp() {
        assertThat(LoggingWhatsAppSender.mask(null)).isEqualTo("****");
    }

    /**
     * O adapter desligado nunca falha: simular indisponibilidade aqui deixaria
     * o resto do sistema sem caminho feliz em desenvolvimento.
     */
    @Test
    void sendingNeverFails() {
        assertThatCode(() -> new LoggingWhatsAppSender().send("+5511988887777", "Oi"))
                .doesNotThrowAnyException();
    }
}
