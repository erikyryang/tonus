import { http, HttpResponse } from 'msw';
import type { Invoice, Subscription, SubscriptionInput } from '@/features/billing/api/billingApi';
import type { Page } from '@/lib/page';
import { pageOf } from './pageOf';
import { problem } from './problem';

export function invoice(overrides: Partial<Invoice> & { id: number }): Invoice {
  return {
    subscriptionId: 100 + overrides.id,
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

export function subscription(overrides: Partial<Subscription> & { id: number }): Subscription {
  return {
    studentId: 200 + overrides.id,
    monthlyAmount: 300,
    dueDay: 10,
    active: true,
    ...overrides,
  };
}

export function subscriptionsHandler(
  resolve: (params: URLSearchParams) => Page<Subscription>,
  log?: string[],
) {
  return http.get('/api/subscriptions', ({ request }) => {
    const url = new URL(request.url);
    log?.push(url.search);
    return HttpResponse.json(resolve(url.searchParams));
  });
}

export function subscriptionsList(...subscriptions: Subscription[]) {
  return subscriptionsHandler(() => pageOf(subscriptions));
}

export function createSubscriptionOk(onRequest?: (body: SubscriptionInput) => void, id = 70) {
  return http.post('/api/subscriptions', async ({ request }) => {
    const body = (await request.json()) as SubscriptionInput;
    onRequest?.(body);
    return HttpResponse.json(subscription({ id, ...body }), { status: 201 });
  });
}

export function updateSubscriptionOk(onRequest?: (id: number, body: SubscriptionInput) => void) {
  return http.put('/api/subscriptions/:id', async ({ params, request }) => {
    const body = (await request.json()) as SubscriptionInput;
    const id = Number(params.id);
    onRequest?.(id, body);
    return HttpResponse.json(subscription({ id, ...body }));
  });
}

export function deactivateSubscriptionOk(onRequest?: (id: number) => void) {
  return http.patch('/api/subscriptions/:id/deactivate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(subscription({ id, active: false }));
  });
}

export function activateSubscriptionOk(onRequest?: (id: number) => void) {
  return http.patch('/api/subscriptions/:id/activate', ({ params }) => {
    const id = Number(params.id);
    onRequest?.(id);
    return HttpResponse.json(subscription({ id, active: true }));
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
