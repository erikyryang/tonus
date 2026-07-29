import { http, HttpResponse } from 'msw';
import type { Invoice } from '@/features/billing/api/billingApi';
import type { Page } from '@/lib/page';

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

export function invoicePage(content: Invoice[], overrides: Partial<Page<Invoice>> = {}): Page<Invoice> {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
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

export function registerPaymentConflict(detail: string, onRequest?: () => void) {
  return http.post('/api/invoices/:id/payment', () => {
    onRequest?.();
    return HttpResponse.json(
      { type: 'about:blank', title: 'Conflict', status: 409, detail },
      { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
    );
  });
}
