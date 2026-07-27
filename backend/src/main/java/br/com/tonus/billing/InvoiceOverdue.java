package br.com.tonus.billing;

import java.time.LocalDate;

/**
 * Uma fatura ABERTA passou do prazo de tolerância e virou ATRASADA.
 *
 * Publicado só na transição (uma vez): a query do job só varre faturas ainda
 * ABERTAS, então rodar o job de novo no mesmo dia não republica.
 * Consome: ninguém ainda. {@code notification} avisará o aluno.
 */
public record InvoiceOverdue(Long invoiceId, Long studentId, LocalDate dueDate) {
}
