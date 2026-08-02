import type { Invoice } from '../api/billingApi';

/**
 * Como a fatura se identifica na tela.
 *
 * `studentName` pode vir nulo: invoice.student_id não tem FK (decisão de
 * docs/ddl.md), então o back não consegue prometer que o aluno existe. Numa
 * linha sobre dinheiro, célula em branco é pior do que um identificador feio —
 * o ADMIN precisa conseguir dizer de quem é a cobrança para ir atrás.
 */
export function studentLabel(invoice: Invoice): string {
  return invoice.studentName ?? `Aluno #${invoice.studentId}`;
}
