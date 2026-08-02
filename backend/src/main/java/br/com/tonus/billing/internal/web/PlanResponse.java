package br.com.tonus.billing.internal.web;

import br.com.tonus.billing.internal.PlanView;
import java.math.BigDecimal;

/**
 * {@code activeEnrollments} é quantos alunos perdem a cobrança se o plano for
 * desativado. Vem do back porque a tela precisa dizer isso antes de confirmar,
 * e contá-lo no cliente exigiria carregar as matrículas de todo o estúdio.
 */
public record PlanResponse(Long id, String name, BigDecimal monthlyPrice, boolean active, long activeEnrollments) {

    public static PlanResponse from(PlanView view) {
        return new PlanResponse(view.plan().getId(), view.plan().getName(), view.plan().getMonthlyPrice(),
                view.plan().isActive(), view.activeEnrollments());
    }
}
