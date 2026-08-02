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
  studentId: number;
  /**
   * Nulo é possível: invoice.student_id não tem FK (docs/ddl.md), então o back
   * não consegue prometer que o aluno existe. A tela resolve o fallback.
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

/**
 * Espelha o DTO de /plans.
 *
 * `activeEnrollments` vem do back porque a tela precisa dizer quantos alunos
 * deixam de ser cobrados antes de desativar um plano, e contá-lo aqui exigiria
 * carregar as matrículas do estúdio inteiro.
 */
export interface Plan {
  id: number;
  name: string;
  monthlyPrice: number;
  active: boolean;
  activeEnrollments: number;
}

export interface PlanInput {
  name: string;
  monthlyPrice: number;
}

export interface PlanQuery {
  active?: boolean;
  page: number;
}

export const plansKey = (query?: PlanQuery) =>
  query ? (['billing', 'plans', query] as const) : (['billing', 'plans'] as const);

/** O catálogo inteiro para os seletores — sem paginação, como o de professores. */
export const plansLookupKey = ['billing', 'plans', 'lookup'] as const;

/** O vínculo aluno -> plano. Desativar não apaga: a linha guarda o histórico. */
export interface PlanEnrollment {
  id: number;
  studentId: number;
  planId: number;
  active: boolean;
}

export const enrollmentsKey = (studentId?: number) =>
  studentId === undefined
    ? (['billing', 'plan-enrollments'] as const)
    : (['billing', 'plan-enrollments', studentId] as const);

/** A configuração do estúdio: uma linha, um campo. */
export interface BillingSettings {
  multiPlanDiscountPercent: number;
}

export const billingSettingsKey = ['billing', 'settings'] as const;

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

  plans: ({ active, page }: PlanQuery) => {
    const params = new URLSearchParams({ page: String(page) });
    if (active !== undefined) params.set('active', String(active));
    return apiClient.get<Page<Plan>>(`/plans?${params}`);
  },
  createPlan: (input: PlanInput) => apiClient.post<Plan>('/plans', input),
  updatePlan: (id: number, input: PlanInput) => apiClient.put<Plan>(`/plans/${id}`, input),
  activatePlan: (id: number) => apiClient.patch<Plan>(`/plans/${id}/activate`),
  deactivatePlan: (id: number) => apiClient.patch<Plan>(`/plans/${id}/deactivate`),

  enrollmentsOfStudent: (studentId: number) =>
    apiClient.get<PlanEnrollment[]>(`/plan-enrollments?studentId=${studentId}`),
  enroll: (studentId: number, planId: number) =>
    apiClient.post<PlanEnrollment>('/plan-enrollments', { studentId, planId }),
  activateEnrollment: (id: number) => apiClient.patch<PlanEnrollment>(`/plan-enrollments/${id}/activate`),
  deactivateEnrollment: (id: number) => apiClient.patch<PlanEnrollment>(`/plan-enrollments/${id}/deactivate`),

  settings: () => apiClient.get<BillingSettings>('/billing-settings'),
  updateSettings: (input: BillingSettings) => apiClient.put<BillingSettings>('/billing-settings', input),

  /** O id do aluno vem do token: /me/invoices não aceita id nenhum. */
  myInvoices: (page: number) => apiClient.get<Page<Invoice>>(`/me/invoices?page=${page}`),

  /**
   * Quem está com fatura ATRASADA, em ids de aluno. Quem cruza com a lista de
   * alunos é a tela: no back, `registration` não consulta `billing`.
   */
  overdueStudentIds: () => apiClient.get<number[]>('/invoices/overdue-student-ids'),
};
