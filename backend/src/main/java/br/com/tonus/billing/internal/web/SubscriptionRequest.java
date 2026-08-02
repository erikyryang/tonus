package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Sem dia de vencimento: ele é o dia do mês em que o aluno se cadastrou, e o
 * cliente não o escolhe nem o edita (SPEC-004). Um campo aqui daria ao ADMIN um
 * controle que a regra não tem — e a assinatura passaria a discordar do aluno
 * a que pertence.
 */
public record SubscriptionRequest(
        @NotNull Long studentId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal monthlyAmount) {
}
