package br.com.tonus.billing.internal;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolução pura de vencimento: dia do cadastro -> data da competência. Sem
 * banco e sem Spring, porque é lógica de domínio sem efeito colateral.
 *
 * Herdou o papel de {@code SubscriptionDueDateTest}, que morreu com a
 * assinatura. A regra não morreu junto — só mudou de casa (SPEC-012).
 */
class DueDatesTest {

    @Test
    void resolvesToTheSameDayWhenMonthHasEnoughDays() {
        LocalDate registration = LocalDate.of(2025, 1, 10);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2026, 7))).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    /**
     * Critério de aceite: dia inexistente no mês (31 em fevereiro) cai no
     * último dia. 2026 não é bissexto, fevereiro tem 28 dias.
     */
    @Test
    void rollsOverToLastDayOfShortMonth() {
        LocalDate registration = LocalDate.of(2025, 1, 31);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    /** E em ano bissexto o último dia é 29, não 28. */
    @Test
    void respectsLeapYear() {
        LocalDate registration = LocalDate.of(2025, 1, 31);

        assertThat(DueDates.forMonth(registration, YearMonth.of(2028, 2))).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
