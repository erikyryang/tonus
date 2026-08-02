package br.com.tonus.billing.internal;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O vencimento de uma competência a partir do dia do cadastro do aluno.
 *
 * Vive fora do serviço porque é regra pura, sem banco e sem relógio: era método
 * de {@code Subscription} e não podia morrer junto com ela (SPEC-012). O
 * aparo para o último dia do mês é a parte que se esquece — dia 31 em fevereiro
 * — e é o que o teste unitário guarda.
 */
public final class DueDates {

    private DueDates() {
    }

    public static LocalDate forMonth(LocalDate registration, YearMonth month) {
        return month.atDay(Math.min(registration.getDayOfMonth(), month.lengthOfMonth()));
    }
}
