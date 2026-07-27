package br.com.tonus.billing.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolução pura de vencimento: dia do mês -> data. Sem dependência de banco
 * ou Spring, porque é lógica de domínio sem efeito colateral.
 */
class SubscriptionDueDateTest {

    @Test
    void resolvesToTheSameDayWhenMonthHasEnoughDays() {
        Subscription subscription = new Subscription(1L, new BigDecimal("200.00"), 10);

        assertThat(subscription.dueDateFor(YearMonth.of(2026, 7))).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    /**
     * Critério de aceite: dia_vencimento inexistente no mês (31 em fevereiro)
     * cai no último dia do mês. 2026 não é bissexto, fevereiro tem 28 dias.
     */
    @Test
    void rollsOverToLastDayOfShortMonth() {
        Subscription subscription = new Subscription(1L, new BigDecimal("200.00"), 31);

        assertThat(subscription.dueDateFor(YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
