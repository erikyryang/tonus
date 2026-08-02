import { http, HttpResponse } from 'msw';
import type {
  BillingSettings,
  Invoice,
  Plan,
  PlanEnrollment,
  PlanInput,
} from '@/features/billing/api/billingApi';
import type { Page } from '@/lib/page';
import { pageOf } from './pageOf';
import { problem } from './problem';

export function invoice(overrides: Partial<Invoice> & { id: number }): Invoice {
  return {
    studentId: 200 + overrides.id,
    studentName: `Aluno ${overrides.id}`,
    referenceMonth: '2026-07',
    amount: 200,
    dueDate: '2026-07-10',
    status: 'OPEN',
    paymentDate: null,
    ...overrides,
  };
}

/**
 * A listagem vem de uma função para que o teste possa trocar a resposta depois
 * da baixa — é assim que se observa "invalidou e refez a chamada" sem espiar o
 * TanStack Query por dentro, mesmo padrão de `dayScheduleHandler`.
 *
 * `log` recebe a query string inteira: o critério de aceite é sobre o que sai
 * na rede, e o filtro só existe se chegar ao backend.
 */
export function invoicesHandler(resolve: (search: URLSearchParams) => Page<Invoice>, log?: string[]) {
  return http.get('/api/invoices', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

/** O callback pode ser assíncrono: é por ele que um teste segura a resposta. */
export function registerPaymentOk(onRequest?: (id: number, paymentDate: string) => void | Promise<void>) {
  return http.post('/api/invoices/:id/payment', async ({ params, request }) => {
    const body = (await request.json()) as { paymentDate: string };
    const id = Number(params.id);
    await onRequest?.(id, body.paymentDate);
    return HttpResponse.json(invoice({ id, status: 'PAID', paymentDate: body.paymentDate }));
  });
}

export function plan(overrides: Partial<Plan> & { id: number }): Plan {
  return {
    name: `Plano ${overrides.id}`,
    monthlyPrice: 350,
    active: true,
    activeEnrollments: 0,
    ...overrides,
  };
}

export function plansHandler(resolve: (params: URLSearchParams) => Page<Plan>, log?: string[]) {
  return http.get('/api/plans', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function plansList(...plans: Plan[]) {
  return plansHandler(() => pageOf(plans));
}

export function createPlanOk(onRequest?: (body: PlanInput) => void, id = 90) {
  return http.post('/api/plans', async ({ request }) => {
    const body = (await request.json()) as PlanInput;
    onRequest?.(body);
    return HttpResponse.json(plan({ id, ...body }), { status: 201 });
  });
}

export function updatePlanOk(onRequest?: (id: number, body: PlanInput) => void) {
  return http.put('/api/plans/:id', async ({ params, request }) => {
    const body = (await request.json()) as PlanInput;
    const id = Number(params.id);
    onRequest?.(id, body);
    return HttpResponse.json(plan({ id, ...body }));
  });
}

export function setPlanActiveOk(onRequest?: (id: number, active: boolean) => void) {
  return [
    http.patch('/api/plans/:id/activate', ({ params }) => {
      onRequest?.(Number(params.id), true);
      return HttpResponse.json(plan({ id: Number(params.id), active: true }));
    }),
    http.patch('/api/plans/:id/deactivate', ({ params }) => {
      onRequest?.(Number(params.id), false);
      return HttpResponse.json(plan({ id: Number(params.id), active: false }));
    }),
  ];
}

export function enrollment(overrides: Partial<PlanEnrollment> & { id: number; planId: number }): PlanEnrollment {
  return { studentId: 1, active: true, ...overrides };
}

/** GET /plan-enrollments?studentId= — os planos de um aluno, sem paginação. */
export function enrollmentsHandler(resolve: (studentId: number) => PlanEnrollment[], log?: number[]) {
  return http.get('/api/plan-enrollments', ({ request }) => {
    const studentId = Number(new URL(request.url).searchParams.get('studentId'));
    log?.push(studentId);
    return HttpResponse.json(resolve(studentId));
  });
}

export function noEnrollments() {
  return enrollmentsHandler(() => []);
}

export function enrollOk(onRequest?: (body: { studentId: number; planId: number }) => void, id = 500) {
  return http.post('/api/plan-enrollments', async ({ request }) => {
    const body = (await request.json()) as { studentId: number; planId: number };
    onRequest?.(body);
    return HttpResponse.json(enrollment({ id, ...body }), { status: 201 });
  });
}

export function setEnrollmentActiveOk(onRequest?: (id: number, active: boolean) => void) {
  return [
    http.patch('/api/plan-enrollments/:id/activate', ({ params }) => {
      onRequest?.(Number(params.id), true);
      return HttpResponse.json(enrollment({ id: Number(params.id), planId: 1, active: true }));
    }),
    http.patch('/api/plan-enrollments/:id/deactivate', ({ params }) => {
      onRequest?.(Number(params.id), false);
      return HttpResponse.json(enrollment({ id: Number(params.id), planId: 1, active: false }));
    }),
  ];
}

export function billingSettings(multiPlanDiscountPercent = 0): BillingSettings {
  return { multiPlanDiscountPercent };
}

export function settingsHandler(resolve: () => BillingSettings) {
  return http.get('/api/billing-settings', () => HttpResponse.json(resolve()));
}

export function updateSettingsOk(onRequest?: (body: BillingSettings) => void) {
  return http.put('/api/billing-settings', async ({ request }) => {
    const body = (await request.json()) as BillingSettings;
    onRequest?.(body);
    return HttpResponse.json(body);
  });
}

/**
 * GET /invoices/overdue-student-ids — quem está com fatura atrasada, em ids.
 *
 * É o que a tela de alunos cruza com a própria lista para dizer "Atrasado" ou
 * "Em dia": o back não junta as duas coisas porque `registration` não consulta
 * `billing`, então quem cruza é o cliente.
 */
export function overdueStudentIdsHandler(resolve: () => number[], log?: string[]) {
  return http.get('/api/invoices/overdue-student-ids', ({ request }) => {
    log?.push(new URL(request.url).search);
    return HttpResponse.json(resolve());
  });
}

export function overdueStudentIds(...ids: number[]) {
  return overdueStudentIdsHandler(() => ids);
}

/** GET /me/invoices — as faturas do aluno logado; o id sai do token. */
export function myInvoicesHandler(resolve: (params: URLSearchParams) => Page<Invoice>, log?: string[]) {
  return http.get('/api/me/invoices', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function myInvoicesList(...invoices: Invoice[]) {
  return myInvoicesHandler(() => pageOf(invoices));
}

export function registerPaymentConflict(detail: string, onRequest?: () => void) {
  return http.post('/api/invoices/:id/payment', () => {
    onRequest?.();
    return problem(409, 'Conflict', detail);
  });
}
