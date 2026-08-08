import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { delay } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Invoice } from '@/features/billing/api/billingApi';
import { todayIsoDate } from '@/lib/dates';
import type { Page } from '@/lib/page';
import { ADMIN, meOk } from '@/test/authHandlers';
import {
  invoice,
  invoicesHandler,
  registerPaymentConflict,
  registerPaymentOk,
} from '@/test/billingHandlers';
import { pageOf } from '@/test/pageOf';
import { createTestQueryClient, renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

const ANA = { id: 1, studentId: 201, studentName: 'Ana Alves' };
const BIA = { id: 2, studentId: 202, studentName: 'Bia Barros' };
const CAIO = { id: 3, studentId: 203, studentName: 'Caio Costa' };

function renderInvoices(queryClient = createTestQueryClient(), route = '/admin/invoices') {
  return renderApp(route, queryClient);
}

/** Always the fixed list, regardless of the filter — for render tests. */
function fixedList(...invoices: Invoice[]) {
  return invoicesHandler(() => pageOf(invoices));
}

function rowOf(studentName: string) {
  return screen.findByRole('row', { name: new RegExp(studentName) });
}

function paramsOf(search: string) {
  return new URLSearchParams(search);
}

describe('lista de faturas', () => {
  it('mostra aluno, competência, valor e vencimento', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ ...ANA, referenceMonth: '2026-07', amount: 250.5, dueDate: '2026-07-10' })),
    );

    renderInvoices();

    const row = await rowOf('Ana Alves');
    expect(within(row).getByText('07/2026')).toBeInTheDocument();
    expect(within(row).getByText(/R\$\s*250,50/)).toBeInTheDocument();
    expect(within(row).getByText('10/07/2026')).toBeInTheDocument();
  });

  /**
   * Acceptance criterion: OVERDUE is what the ADMIN hunts for on this screen. The highlight can't
   * be color alone — the badge's text distinguishes it, and `data-status` is the hook
   * the styling uses for the whole row.
   */
  it('destaca a fatura atrasada', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ ...ANA, status: 'OVERDUE' }), invoice({ ...BIA, status: 'OPEN' })),
    );

    renderInvoices();

    const overdue = await rowOf('Ana Alves');
    expect(within(overdue).getByText('Atrasada')).toBeInTheDocument();
    expect(overdue).toHaveAttribute('data-status', 'OVERDUE');

    const open = await rowOf('Bia Barros');
    expect(within(open).getByText('Aberta')).toBeInTheDocument();
    expect(open).toHaveAttribute('data-status', 'OPEN');
  });

  /** Payment is immutable in the back: nothing to settle to offer, only the record of what happened. */
  it('fatura paga mostra a data do pagamento e não oferece renovar', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ ...ANA, status: 'PAID', paymentDate: '2026-07-08' })),
    );

    renderInvoices();

    const row = await rowOf('Ana Alves');
    expect(within(row).getByText('Paga')).toBeInTheDocument();
    expect(within(row).getByText(/08\/07\/2026/)).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /renovar/i })).not.toBeInTheDocument();
  });

  it('oferece renovar em fatura aberta e em atrasada', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(
        invoice({ ...ANA, status: 'OPEN' }),
        invoice({ ...BIA, status: 'OVERDUE' }),
        invoice({ ...CAIO, status: 'PAID', paymentDate: '2026-07-08' }),
      ),
    );

    renderInvoices();

    expect(await screen.findByRole('button', { name: 'Renovar a fatura de Ana Alves' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Renovar a fatura de Bia Barros' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Renovar a fatura de Caio Costa' })).not.toBeInTheDocument();
  });

  it('mostra vazio explícito quando não há fatura', async () => {
    server.use(meOk(ADMIN), fixedList());

    renderInvoices();

    expect(await screen.findByText(/nenhuma fatura registrada/i)).toBeInTheDocument();
  });

  /** "No invoices" under the "Overdue" filter would read as "there are no invoices at all". */
  it('o vazio diz de qual filtro está falando', async () => {
    server.use(meOk(ADMIN), fixedList());

    const { user } = renderInvoices();

    await user.click(await screen.findByRole('button', { name: 'Atrasadas' }));

    expect(await screen.findByText(/nenhuma fatura atrasada/i)).toBeInTheDocument();
  });

  /**
   * invoice.student_id has no FK (decision from docs/ddl.md), so the back
   * can return a null name. A blank cell in a row about money is worse
   * than an ugly identifier.
   */
  it('identifica o aluno mesmo sem nome vindo do back', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ id: 1, studentId: 201, studentName: null, status: 'OPEN' })),
    );

    renderInvoices();

    expect(await screen.findByText('Aluno #201')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Renovar a fatura de Aluno #201' })).toBeInTheDocument();
  });

  it('dá nome acessível à tabela', async () => {
    server.use(meOk(ADMIN), fixedList(invoice({ ...ANA, status: 'OPEN' })));

    renderInvoices();

    expect(await screen.findByRole('table', { name: 'Faturas' })).toBeInTheDocument();
  });
});

describe('filtro por status', () => {
  /** Acceptance criterion: the filter only exists if it reaches the backend. */
  it('altera a query string enviada', async () => {
    const sent: string[] = [];
    server.use(
      meOk(ADMIN),
      invoicesHandler((params) => {
        const status = params.get('status');
        return pageOf(
          [invoice({ ...ANA, status: 'OPEN' }), invoice({ ...BIA, status: 'OVERDUE' })].filter(
            (each) => status == null || each.status === status,
          ),
        );
      }, sent),
    );

    const { user } = renderInvoices();

    // Default is "all": no status goes into the query.
    await rowOf('Ana Alves');
    expect(paramsOf(sent[0]!).get('status')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Atrasadas' }));

    await waitFor(() => expect(sent).toHaveLength(2));
    expect(paramsOf(sent[1]!).get('status')).toBe('OVERDUE');
    expect(await rowOf('Bia Barros')).toBeInTheDocument();
    expect(screen.queryByRole('row', { name: /Ana Alves/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Abertas' }));

    await waitFor(() => expect(sent).toHaveLength(3));
    expect(paramsOf(sent[2]!).get('status')).toBe('OPEN');
  });
});

describe('renovar', () => {
  async function openPaymentDialog(user: ReturnType<typeof renderInvoices>['user'], studentName = 'Ana Alves') {
    await user.click(await screen.findByRole('button', { name: `Renovar a fatura de ${studentName}` }));
    return screen.findByRole('dialog');
  }

  /**
   * Acceptance criterion: the whole flow. It's the UI action with the biggest consequences —
   * it triggers a real receipt via WhatsApp.
   */
  it('confirma, envia a data escolhida e a lista volta com a fatura paga', async () => {
    const sent: Array<{ id: number; paymentDate: string }> = [];
    let paid = false;

    server.use(
      meOk(ADMIN),
      invoicesHandler(() =>
        pageOf([
          paid
            ? invoice({ ...ANA, status: 'PAID', paymentDate: '2026-07-20' })
            : invoice({ ...ANA, status: 'OPEN' }),
        ]),
      ),
      registerPaymentOk((id, paymentDate) => {
        sent.push({ id, paymentDate });
        paid = true;
      }),
    );

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);

    // The dialog states the real effect, doesn't ask "are you sure?".
    expect(within(dialog).getByText(/ciclo do aluno é renovado/i)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Data do pagamento'), { target: { value: '2026-07-20' } });
    // Settling no longer touches the due date (SPEC-004): promising that would be a lie.
    expect(within(dialog).queryByText(/vencimento da assinatura/i)).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    await waitFor(() => expect(sent).toEqual([{ id: 1, paymentDate: '2026-07-20' }]));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    const row = await rowOf('Ana Alves');
    expect(within(row).getByText('Paga')).toBeInTheDocument();
    expect(within(row).getByText(/20\/07\/2026/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/renovação registrada/i));
  });

  it('já vem com a data de hoje preenchida', async () => {
    server.use(meOk(ADMIN), fixedList(invoice({ ...ANA, status: 'OPEN' })));

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);

    expect(within(dialog).getByLabelText('Data do pagamento')).toHaveValue(todayIsoDate());
  });

  /** Acceptance criterion: zod blocks it before any request. */
  it('recusa data futura sem chamar a API', async () => {
    server.use(meOk(ADMIN), fixedList(invoice({ ...ANA, status: 'OPEN' })));

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);

    fireEvent.change(within(dialog).getByLabelText('Data do pagamento'), { target: { value: '2099-01-01' } });
    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    // No POST handler: if the call went out, MSW would fail the test.
    expect(await within(dialog).findByText(/data futura/i)).toBeInTheDocument();
  });

  /**
   * Acceptance criterion: 409 is a legitimate state (another admin settled it, or the
   * double click won the race), not a generic error. The UI explains it and shows the
   * true list.
   */
  it('explica o 409 com a mensagem do back e refaz a lista', async () => {
    const requests: string[] = [];
    // The real race: the screen loaded with the invoice open and, between opening
    // the dialog and confirming, another admin settled it.
    let paidByOtherAdmin = false;

    server.use(
      meOk(ADMIN),
      invoicesHandler(
        () =>
          pageOf([
            paidByOtherAdmin
              ? invoice({ ...ANA, status: 'PAID', paymentDate: '2026-07-19' })
              : invoice({ ...ANA, status: 'OPEN' }),
          ]),
        requests,
      ),
      registerPaymentConflict('Fatura já paga: 1', () => {
        paidByOtherAdmin = true;
      }),
    );

    // Infinite staleTime: a second call can only come from invalidation.
    const { user } = renderInvoices(createTestQueryClient({ staleTime: Infinity, gcTime: Infinity }));

    const dialog = await openPaymentDialog(user);
    expect(requests).toHaveLength(1);

    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    expect(await within(dialog).findByText('Fatura já paga: 1')).toBeInTheDocument();
    await waitFor(() => expect(requests).toHaveLength(2));

    const row = await rowOf('Ana Alves');
    expect(within(row).getByText('Paga')).toBeInTheDocument();
  });

  /**
   * The 409 here isn't transient: the invoice is paid and will stay paid.
   * Leaving the button armed invites repeating an action that can only fail again.
   */
  it('não deixa repetir a renovação depois do 409', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ ...ANA, status: 'OPEN' })),
      registerPaymentConflict('Fatura já paga: 1'),
    );

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    expect(await within(dialog).findByText('Fatura já paga: 1')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Renovar' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Voltar' })).toBeEnabled();
  });

  /**
   * WhatsApp delivery is asynchronous (event + at-least-once redelivery). The
   * toast can't state that the student has already received it — at the moment of the HTTP
   * response, they haven't.
   */
  it('o toast não afirma entrega que ainda não aconteceu', async () => {
    server.use(meOk(ADMIN), fixedList(invoice({ ...ANA, status: 'OPEN' })), registerPaymentOk());

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);
    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/renovação registrada/i));
    expect(screen.getByRole('status')).not.toHaveTextContent(/recebeu/i);
  });

  /** Double protection with the 409; the real idempotence is uk_payment_invoice. */
  it('desabilita o botão enquanto a renovação está em andamento', async () => {
    server.use(
      meOk(ADMIN),
      fixedList(invoice({ ...ANA, status: 'OPEN' })),
      registerPaymentOk(async () => {
        await delay(50);
      }),
    );

    const { user } = renderInvoices();
    const dialog = await openPaymentDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Renovar' }));

    const busy = await within(dialog).findByRole('button', { name: /renovando/i });
    expect(busy).toBeDisabled();
  });
});

/** The front's Page type is the slice of Spring's Page; pagination uses `number`. */
describe('paginação', () => {
  it('navega para a próxima página e leva o filtro junto', async () => {
    const sent: string[] = [];
    const pages: Record<number, Page<Invoice>> = {
      0: pageOf([invoice({ ...ANA, status: 'OVERDUE' })], { number: 0, totalElements: 2, totalPages: 2 }),
      1: pageOf([invoice({ ...BIA, status: 'OVERDUE' })], { number: 1, totalElements: 2, totalPages: 2 }),
    };

    server.use(
      meOk(ADMIN),
      invoicesHandler((params) => pages[Number(params.get('page') ?? 0)]!, sent),
    );

    const { user } = renderInvoices();

    await user.click(await screen.findByRole('button', { name: 'Atrasadas' }));
    await waitFor(() => expect(sent).toHaveLength(2));

    await user.click(screen.getByRole('button', { name: 'Próxima página' }));

    await waitFor(() => expect(sent).toHaveLength(3));
    const last = paramsOf(sent[2]!);
    expect(last.get('page')).toBe('1');
    expect(last.get('status')).toBe('OVERDUE');
    expect(await rowOf('Bia Barros')).toBeInTheDocument();
  });

  /**
   * The last page vanishes under the ADMIN when they settle the only item
   * on it under the "Overdue" filter: the invoice leaves the filter and the page stops
   * existing. Without stepping back, the screen would say "no overdue invoices" with page 1
   * still full.
   */
  it('recua quando a página deixa de existir depois da renovação', async () => {
    let paid = false;
    const sent: string[] = [];

    server.use(
      meOk(ADMIN),
      invoicesHandler((params) => {
        const page = Number(params.get('page') ?? 0);
        if (paid) {
          // Only Ana is left: one page, and page 1 no longer exists.
          return page === 0
            ? pageOf([invoice({ ...ANA, status: 'OVERDUE' })], { number: 0, totalElements: 1, totalPages: 1 })
            : pageOf([], { number: page, totalElements: 1, totalPages: 1 });
        }
        return page === 0
          ? pageOf([invoice({ ...ANA, status: 'OVERDUE' })], { number: 0, totalElements: 2, totalPages: 2 })
          : pageOf([invoice({ ...BIA, status: 'OVERDUE' })], { number: 1, totalElements: 2, totalPages: 2 });
      }, sent),
      registerPaymentOk(() => {
        paid = true;
      }),
    );

    const { user } = renderInvoices(createTestQueryClient(), '/admin/invoices?status=OVERDUE&page=1');

    await user.click(await screen.findByRole('button', { name: 'Renovar a fatura de Bia Barros' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Renovar' }));

    // Stepped back to page 0 and shows what's left, instead of page 1's empty state.
    expect(await rowOf('Ana Alves')).toBeInTheDocument();
    expect(screen.queryByText(/nenhuma fatura/i)).not.toBeInTheDocument();
    // The URL loses the ?page=, but the call always carries an explicit page.
    expect(paramsOf(sent[sent.length - 1]!).get('page')).toBe('0');
  });
});
