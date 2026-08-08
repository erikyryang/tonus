import { apiClient } from '@/lib/apiClient';
import type { Page } from '@/lib/page';

export type InvoiceStatus = 'OPEN' | 'PAID' | 'OVERDUE';

/**
 * Mirrors the GET /invoices DTO.
 *
 * `studentName` and `paymentDate` come from the back by SPEC-008's decision: resolving the
 * student's name in the front would cost one call per row, and the payment date
 * lives in the `payment` table, which has no endpoint of its own.
 */
export interface Invoice {
  id: number;
  studentId: number;
  /**
   * Null is possible: invoice.student_id has no FK (docs/ddl.md), so the back
   * can't promise the student exists. The screen handles the fallback.
   */
  studentName: string | null;
  /** yyyy-MM — reference month, the month the invoice refers to. */
  referenceMonth: string;
  amount: number;
  dueDate: string;
  status: InvoiceStatus;
  /** Only exists on a PAID invoice. */
  paymentDate: string | null;
}

export interface InvoiceQuery {
  status?: InvoiceStatus;
  page: number;
}

/**
 * With no argument it returns the prefix ['billing','invoices'] — that's how
 * settling a payment invalidates every filter and page combination at once.
 */
export const invoicesKey = (query?: InvoiceQuery) =>
  query ? (['billing', 'invoices', query] as const) : (['billing', 'invoices'] as const);

/**
 * Mirrors the /plans DTO.
 *
 * `activeEnrollments` comes from the back because the screen needs to say how many students
 * stop being charged before deactivating a plan, and counting it here would require
 * loading the whole studio's enrollments.
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

/** The whole catalog for the selectors — no pagination, like the teachers' one. */
export const plansLookupKey = ['billing', 'plans', 'lookup'] as const;

/** The student -> plan relationship. Deactivating doesn't delete: the row keeps the history. */
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

/** The studio's configuration: one row, one field. */
export interface BillingSettings {
  multiPlanDiscountPercent: number;
}

export const billingSettingsKey = ['billing', 'settings'] as const;

export const myInvoicesKey = (page: number) => ['billing', 'me', 'invoices', page] as const;

/**
 * No parameter: the response is the whole set of who's overdue, and it's the
 * same for any page or filter on the students screen. Keying by page
 * would redo the call on every navigation just to receive the same list.
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

  /** The student id comes from the token: /me/invoices doesn't accept any id. */
  myInvoices: (page: number) => apiClient.get<Page<Invoice>>(`/me/invoices?page=${page}`),

  /**
   * Who has an OVERDUE invoice, as student ids. Whoever cross-references the list of
   * students is the screen: in the back, `registration` doesn't query `billing`.
   */
  overdueStudentIds: () => apiClient.get<number[]>('/invoices/overdue-student-ids'),
};
