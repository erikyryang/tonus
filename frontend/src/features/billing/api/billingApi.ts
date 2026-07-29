import { apiClient } from '@/lib/apiClient';
import type { Page } from '@/lib/page';

export type InvoiceStatus = 'OPEN' | 'PAID' | 'OVERDUE';

/**
 * Espelha o DTO de GET /invoices.
 *
 * `studentName` e `paymentDate` vêm do back por decisão da SPEC-008: resolver o
 * nome do aluno no front custaria uma chamada por linha, e a data do pagamento
 * mora na tabela `payment`, que não tem endpoint próprio.
 */
export interface Invoice {
  id: number;
  subscriptionId: number;
  studentId: number;
  /**
   * Nulo é possível: subscription.student_id não tem FK (docs/ddl.md), então o
   * back não consegue prometer que o aluno existe. A tela resolve o fallback.
   */
  studentName: string | null;
  /** yyyy-MM — competência, o mês a que a fatura se refere. */
  referenceMonth: string;
  amount: number;
  dueDate: string;
  status: InvoiceStatus;
  /** Só existe em fatura PAGA. */
  paymentDate: string | null;
}

export interface InvoiceQuery {
  status?: InvoiceStatus;
  page: number;
}

/**
 * Sem argumento devolve o prefixo ['billing','invoices'] — é por ele que a
 * baixa invalida todas as combinações de filtro e página de uma vez.
 */
export const invoicesKey = (query?: InvoiceQuery) =>
  query ? (['billing', 'invoices', query] as const) : (['billing', 'invoices'] as const);

export const billingApi = {
  invoices: ({ status, page }: InvoiceQuery) => {
    const params = new URLSearchParams({ page: String(page) });
    if (status) params.set('status', status);
    return apiClient.get<Page<Invoice>>(`/invoices?${params}`);
  },
  registerPayment: (id: number, paymentDate: string) =>
    apiClient.post<Invoice>(`/invoices/${id}/payment`, { paymentDate }),
};
