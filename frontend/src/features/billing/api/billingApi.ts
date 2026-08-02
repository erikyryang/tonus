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

/** Espelha o DTO de /subscriptions. */
export interface Subscription {
  id: number;
  studentId: number;
  monthlyAmount: number;
  /** 1–31; o back ajusta para o último dia em mês que não tem esse dia. */
  dueDay: number;
  active: boolean;
}

export interface SubscriptionInput {
  studentId: number;
  monthlyAmount: number;
  dueDay: number;
}

export interface SubscriptionQuery {
  active?: boolean;
  page: number;
}

export const subscriptionsKey = (query?: SubscriptionQuery) =>
  query ? (['billing', 'subscriptions', query] as const) : (['billing', 'subscriptions'] as const);

export const myInvoicesKey = (page: number) => ['billing', 'me', 'invoices', page] as const;

/**
 * Sem parâmetro: a resposta é o conjunto inteiro de quem está atrasado, e é o
 * mesmo para qualquer página ou filtro da tela de alunos. Chavear por página
 * refaria a chamada a cada navegação para receber a mesma lista.
 */
export const overdueStudentIdsKey = ['billing', 'overdue-ids'] as const;

export const billingApi = {
  invoices: ({ status, page }: InvoiceQuery) => {
    const params = new URLSearchParams({ page: String(page) });
    if (status) params.set('status', status);
    return apiClient.get<Page<Invoice>>(`/invoices?${params}`);
  },
  registerPayment: (id: number, paymentDate: string) =>
    apiClient.post<Invoice>(`/invoices/${id}/payment`, { paymentDate }),

  subscriptions: ({ active, page }: SubscriptionQuery) => {
    const params = new URLSearchParams({ page: String(page) });
    if (active !== undefined) params.set('active', String(active));
    return apiClient.get<Page<Subscription>>(`/subscriptions?${params}`);
  },
  createSubscription: (input: SubscriptionInput) => apiClient.post<Subscription>('/subscriptions', input),
  /**
   * O aluno vai no corpo mesmo na edição: o back exige o campo, e ignora a
   * troca — assinatura não muda de dono, cria-se outra.
   */
  updateSubscription: (id: number, input: SubscriptionInput) =>
    apiClient.put<Subscription>(`/subscriptions/${id}`, input),
  activateSubscription: (id: number) => apiClient.patch<Subscription>(`/subscriptions/${id}/activate`),
  deactivateSubscription: (id: number) => apiClient.patch<Subscription>(`/subscriptions/${id}/deactivate`),

  /** O id do aluno vem do token: /me/invoices não aceita id nenhum. */
  myInvoices: (page: number) => apiClient.get<Page<Invoice>>(`/me/invoices?page=${page}`),

  /**
   * Quem está com fatura ATRASADA, em ids de aluno. Quem cruza com a lista de
   * alunos é a tela: no back, `registration` não consulta `billing`.
   */
  overdueStudentIds: () => apiClient.get<number[]>('/invoices/overdue-student-ids'),
};
