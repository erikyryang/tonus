/**
 * Cobrança: ciclo assinatura -> fatura -> pagamento, com job diário.
 *
 * Depende de registration porque não existe cobrança sem aluno — a fachada
 * valida vínculo e estado do aluno.
 *
 * A aresta é acíclica: registration não conhece billing. Efeito colateral
 * para fora do módulo é evento (InvoiceDueSoon, InvoiceOverdue,
 * PaymentRegistered), nunca chamada direta.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.billing;
