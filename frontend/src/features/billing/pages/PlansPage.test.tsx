import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Plan, PlanInput } from '@/features/billing/api/billingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import {
  createPlanOk,
  plan,
  plansHandler,
  plansList,
  setPlanActiveOk,
  updatePlanOk,
} from '@/test/billingHandlers';
import { pageOf } from '@/test/pageOf';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

const MUSCULACAO = plan({ id: 3, name: 'Musculação', monthlyPrice: 350.5, activeEnrollments: 4 });

describe('lista de planos', () => {
  it('mostra nome, valor e quantos alunos estão matriculados', async () => {
    server.use(meOk(ADMIN), plansList(MUSCULACAO));

    renderApp('/admin/plans');

    const row = await screen.findByRole('row', { name: /Musculação/ });
    expect(within(row).getByText(/R\$\s*350,50/)).toBeInTheDocument();
    expect(within(row).getByText('4')).toBeInTheDocument();
  });

  it('mostra vazio explícito quando não há plano', async () => {
    server.use(meOk(ADMIN), plansList());

    renderApp('/admin/plans');

    expect(await screen.findByText(/nenhum plano ativo/i)).toBeInTheDocument();
  });
});

describe('cadastro de plano', () => {
  it('cria o plano e a lista volta com ele', async () => {
    const sent: PlanInput[] = [];
    const plans: Plan[] = [];

    server.use(
      meOk(ADMIN),
      plansHandler(() => pageOf(plans)),
      createPlanOk((body) => {
        sent.push(body);
        plans.push(plan({ id: 90, ...body }));
      }),
    );

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Novo plano' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Pilates');
    await user.type(within(dialog).getByLabelText(/valor mensal/i), '270,00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ name: 'Pilates', monthlyPrice: 270 }]));
    expect(await screen.findByRole('row', { name: /Pilates/ })).toBeInTheDocument();
  });

  it('recusa valor zerado sem chamar a API', async () => {
    let called = false;
    server.use(
      meOk(ADMIN),
      plansList(),
      createPlanOk(() => {
        called = true;
      }),
    );

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Novo plano' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Nome'), 'Grátis');
    await user.type(within(dialog).getByLabelText(/valor mensal/i), '0,00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText(/maior que zero/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('edita nome e valor', async () => {
    const sent: Array<{ id: number; body: PlanInput }> = [];
    server.use(meOk(ADMIN), plansList(MUSCULACAO), updatePlanOk((id, body) => sent.push({ id, body })));

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Ações de Musculação' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Editar' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText(/valor mensal/i)).toHaveValue('350,50');

    await user.clear(within(dialog).getByLabelText(/valor mensal/i));
    await user.type(within(dialog).getByLabelText(/valor mensal/i), '380,00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ id: 3, body: { name: 'Musculação', monthlyPrice: 380 } }]));
  });
});

describe('desativação de plano', () => {
  /**
   * SPEC-012's decision is to allow deactivating with an active enrollment — and pay
   * the price in visibility: the confirmation says how many students stop being
   * charged. Without the number, the ADMIN would end a plan without knowing the size of the
   * damage.
   */
  it('avisa quantos alunos deixam de ser cobrados', async () => {
    const changed: Array<{ id: number; active: boolean }> = [];
    server.use(
      meOk(ADMIN),
      plansList(MUSCULACAO),
      ...setPlanActiveOk((id, active) => changed.push({ id, active })),
    );

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Ações de Musculação' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desativar' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/4 alunos deixam de ser cobrados/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Desativar' }));

    await waitFor(() => expect(changed).toEqual([{ id: 3, active: false }]));
  });

  /** A plan with nobody enrolled doesn't invent an alarm. */
  it('não fala em alunos quando não há matrícula', async () => {
    server.use(
      meOk(ADMIN),
      plansList(plan({ id: 4, name: 'Plano Vazio', activeEnrollments: 0 })),
      ...setPlanActiveOk(),
    );

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Ações de Plano Vazio' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desativar' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByText(/deixam de ser cobrados/i)).not.toBeInTheDocument();
  });

  it('não oferece excluir', async () => {
    server.use(meOk(ADMIN), plansList(MUSCULACAO));

    const { user } = renderApp('/admin/plans');

    await user.click(await screen.findByRole('button', { name: 'Ações de Musculação' }));

    expect(await screen.findByRole('menuitem', { name: 'Editar' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Excluir' })).not.toBeInTheDocument();
  });
});
