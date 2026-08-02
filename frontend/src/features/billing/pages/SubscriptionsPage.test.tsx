import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Subscription, SubscriptionInput } from '@/features/billing/api/billingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import {
  createSubscriptionOk,
  deactivateSubscriptionOk,
  subscription,
  subscriptionsHandler,
  subscriptionsList,
  updateSubscriptionOk,
} from '@/test/billingHandlers';
import { pageOf } from '@/test/pageOf';
import { student, studentsList } from '@/test/registrationHandlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

const ANA = student({ id: 1, name: 'Ana Alves' });
const BIA = student({ id: 2, name: 'Bia Barros', active: false });

describe('lista de assinaturas', () => {
  it('mostra aluno, valor e dia de vencimento', async () => {
    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      subscriptionsList(subscription({ id: 3, studentId: 1, monthlyAmount: 350.5, dueDay: 10 })),
    );

    renderApp('/admin/subscriptions');

    const row = await screen.findByRole('row', { name: /Ana Alves/ });
    expect(within(row).getByText(/R\$\s*350,50/)).toBeInTheDocument();
    expect(within(row).getByText(/dia 10/i)).toBeInTheDocument();
  });
});

describe('cadastro de assinatura', () => {
  it('cria a assinatura e a lista volta com ela', async () => {
    const sent: SubscriptionInput[] = [];
    const subscriptions: Subscription[] = [];

    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      subscriptionsHandler(() => pageOf(subscriptions)),
      createSubscriptionOk((body) => {
        sent.push(body);
        subscriptions.push(subscription({ id: 70, ...body }));
      }),
    );

    const { user } = renderApp('/admin/subscriptions');

    await user.click(await screen.findByRole('button', { name: 'Nova assinatura' }));
    const dialog = await screen.findByRole('dialog');

    await user.selectOptions(within(dialog).getByLabelText('Aluno'), '1');
    await user.type(within(dialog).getByLabelText(/valor mensal/i), '350,50');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ studentId: 1, monthlyAmount: 350.5 }]));
    expect(await screen.findByRole('row', { name: /Ana Alves/ })).toBeInTheDocument();
  });

  /**
   * O dia de vencimento deixou de ser escolha: é o dia do mês em que o aluno se
   * cadastrou (SPEC-004). Um campo aqui prometeria uma edição que o back não faz.
   */
  it('não pede dia de vencimento e explica de onde ele vem', async () => {
    server.use(meOk(ADMIN), studentsList(ANA), subscriptionsList());

    const { user } = renderApp('/admin/subscriptions');

    await user.click(await screen.findByRole('button', { name: 'Nova assinatura' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).queryByLabelText(/dia de vencimento/i)).not.toBeInTheDocument();
    expect(within(dialog).getByText(/dia do cadastro do aluno/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/último dia do mês/i)).toBeInTheDocument();
  });

  it('o select de aluno só oferece ativos', async () => {
    server.use(meOk(ADMIN), studentsList(ANA, BIA), subscriptionsList());

    const { user } = renderApp('/admin/subscriptions');

    await user.click(await screen.findByRole('button', { name: 'Nova assinatura' }));
    const select = within(await screen.findByRole('dialog')).getByLabelText('Aluno');

    expect(within(select).getByRole('option', { name: 'Ana Alves' })).toBeInTheDocument();
    expect(within(select).queryByRole('option', { name: 'Bia Barros' })).not.toBeInTheDocument();
  });
});

describe('edição de assinatura', () => {
  /**
   * Assinatura não troca de dono — o back ignora o aluno no PUT, então a tela
   * não oferece a troca em vez de fingir que ela funciona. O aluno vai no corpo
   * porque o back exige o campo.
   */
  it('mantém o aluno e envia o valor novo', async () => {
    const sent: Array<{ id: number; body: SubscriptionInput }> = [];
    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      subscriptionsList(subscription({ id: 3, studentId: 1, monthlyAmount: 300, dueDay: 5 })),
      updateSubscriptionOk((id, body) => sent.push({ id, body })),
    );

    const { user } = renderApp('/admin/subscriptions');

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Editar' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText('Aluno')).toBeDisabled();
    expect(within(dialog).getByLabelText(/valor mensal/i)).toHaveValue('300,00');

    await user.clear(within(dialog).getByLabelText(/valor mensal/i));
    await user.type(within(dialog).getByLabelText(/valor mensal/i), '420,00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ id: 3, body: { studentId: 1, monthlyAmount: 420 } }]));
  });
});

describe('desativação de assinatura', () => {
  /** Desativar para de gerar fatura daqui pra frente; o que já foi emitido fica. */
  it('avisa o que acontece com as faturas já emitidas', async () => {
    const deactivated: number[] = [];
    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      subscriptionsList(subscription({ id: 3, studentId: 1 })),
      deactivateSubscriptionOk((id) => deactivated.push(id)),
    );

    const { user } = renderApp('/admin/subscriptions');

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desativar' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/faturas já emitidas continuam/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Desativar' }));

    await waitFor(() => expect(deactivated).toEqual([3]));
  });
});
