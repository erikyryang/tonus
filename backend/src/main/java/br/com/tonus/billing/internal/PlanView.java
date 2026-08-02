package br.com.tonus.billing.internal;

import br.com.tonus.billing.internal.domain.Plan;

/**
 * Plano pareado com o que a tela precisa e a linha de `plan` não guarda: quantos
 * alunos perdem a cobrança se ele for desativado.
 *
 * Mesma razão de {@link InvoiceView} existir — a contagem mora em outra tabela,
 * e resolvê-la no cliente custaria carregar as matrículas do estúdio inteiro.
 */
public record PlanView(Plan plan, long activeEnrollments) {
}
