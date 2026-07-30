import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { STUDENT, TEACHER, meOk } from '@/test/authHandlers';
import { invoice, myInvoicesList } from '@/test/billingHandlers';
import { renderApp } from '@/test/renderApp';
import { anyMyGridEmpty } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

describe('minhas faturas (aluno)', () => {
  it('mostra competência, valor, vencimento e status', async () => {
    server.use(
      meOk(STUDENT),
      myInvoicesList(
        invoice({ id: 1, referenceMonth: '2026-07', amount: 350.5, dueDate: '2026-07-10', status: 'OPEN' }),
      ),
    );

    renderApp('/me/invoices');

    expect(await screen.findByRole('heading', { name: 'Minhas faturas' })).toBeInTheDocument();
    const item = await screen.findByRole('listitem', { name: /07\/2026/ });
    expect(within(item).getByText(/R\$\s*350,50/)).toBeInTheDocument();
    expect(within(item).getByText(/10\/07\/2026/)).toBeInTheDocument();
    expect(within(item).getByText('Aberta')).toBeInTheDocument();
  });

  /** A atrasada é a que o aluno precisa achar sozinho — e não só pela cor. */
  it('destaca a fatura atrasada', async () => {
    server.use(
      meOk(STUDENT),
      myInvoicesList(
        invoice({ id: 1, referenceMonth: '2026-06', status: 'OVERDUE' }),
        invoice({ id: 2, referenceMonth: '2026-07', status: 'OPEN' }),
      ),
    );

    renderApp('/me/invoices');

    const overdue = await screen.findByRole('listitem', { name: /06\/2026/ });
    expect(within(overdue).getByText('Atrasada')).toBeInTheDocument();
    expect(overdue).toHaveAttribute('data-status', 'OVERDUE');
  });

  it('mostra vazio explícito quando não há fatura', async () => {
    server.use(meOk(STUDENT), myInvoicesList());

    renderApp('/me/invoices');

    expect(await screen.findByText(/nenhuma fatura/i)).toBeInTheDocument();
  });

  /** Guard do papel: fatura de aluno é do aluno. */
  it('manda o professor para a própria grade', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());

    renderApp('/me/invoices');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
  });
});
