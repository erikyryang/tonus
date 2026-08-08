package br.com.tonus.notification.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The phone mask.
 *
 * It is a project privacy rule — "never log the full phone number" — and so it
 * has a direct test instead of depending on someone reading the log and
 * noticing. The degenerate cases exist because it is precisely there that a
 * naive mask blows up or lets the whole number through.
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
     * The disabled adapter never fails: simulating unavailability here would
     * leave the rest of the system without a happy path in development.
     */
    @Test
    void sendingNeverFails() {
        assertThatCode(() -> new LoggingWhatsAppSender().send("+5511988887777", "Oi"))
                .doesNotThrowAnyException();
    }
}
