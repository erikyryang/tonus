package br.com.tonus.billing.internal.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Corpo da baixa: só a data em que o dinheiro entrou. Quem registrou vem do
 * principal autenticado, nunca do cliente.
 *
 * A data é obrigatória, e não "hoje por omissão": este endpoint dispara recibo
 * por WhatsApp e move o vencimento da assinatura, e nenhuma das duas coisas
 * tem desfazer. Um corpo esquecido tem de virar 400, não um pagamento lançado
 * numa data que ninguém escolheu.
 *
 * A recusa de data futura mora no BillingService, não em {@code @PastOrPresent}:
 * o Bean Validation compara com o relógio do sistema, e "hoje" neste projeto só
 * vem do Clock injetado.
 */
public record PaymentRequest(@NotNull LocalDate paymentDate) {
}
