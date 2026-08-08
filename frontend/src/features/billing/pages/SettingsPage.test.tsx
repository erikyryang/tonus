import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import type { BillingSettings } from '@/features/billing/api/billingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { billingSettings, settingsHandler, updateSettingsOk } from '@/test/billingHandlers';
import { problem } from '@/test/problem';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('configuração de cobrança', () => {
  it('mostra o desconto atual e salva o novo', async () => {
    const sent: BillingSettings[] = [];
    server.use(meOk(ADMIN), settingsHandler(() => billingSettings(10)), updateSettingsOk((body) => sent.push(body)));

    const { user } = renderApp('/admin/settings');

    const field = await screen.findByLabelText(/desconto/i);
    expect(field).toHaveValue('10');

    await user.clear(field);
    await user.type(field, '15');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ multiPlanDiscountPercent: 15 }]));
  });

  /** The field explains when the discount applies — otherwise "15" means nothing. */
  it('explica que o desconto só vale a partir do segundo plano', async () => {
    server.use(meOk(ADMIN), settingsHandler(() => billingSettings(0)));

    renderApp('/admin/settings');

    expect(await screen.findByText(/a partir do segundo plano/i)).toBeInTheDocument();
  });

  it('recusa percentual fora de 0 a 100 sem chamar a API', async () => {
    let called = false;
    server.use(
      meOk(ADMIN),
      settingsHandler(() => billingSettings(0)),
      http.put('/api/billing-settings', () => {
        called = true;
        return problem(400, 'Bad Request', 'não deveria ter sido chamado');
      }),
    );

    const { user } = renderApp('/admin/settings');

    const field = await screen.findByLabelText(/desconto/i);
    await user.clear(field);
    await user.type(field, '150');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText(/entre 0 e 100/i)).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('mostra o erro do back sem perder o que foi digitado', async () => {
    server.use(
      meOk(ADMIN),
      settingsHandler(() => billingSettings(0)),
      http.put('/api/billing-settings', () => problem(400, 'Bad Request', 'Percentual inválido')),
    );

    const { user } = renderApp('/admin/settings');

    const field = await screen.findByLabelText(/desconto/i);
    await user.clear(field);
    await user.type(field, '20');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Percentual inválido');
    expect(screen.getByLabelText(/desconto/i)).toHaveValue('20');
  });

  it('guarda a tela do papel errado', async () => {
    server.use(meOk({ ...ADMIN, role: 'STUDENT' }));

    renderApp('/admin/settings');

    await waitFor(() => expect(within(document.body).queryByLabelText(/desconto/i)).not.toBeInTheDocument());
  });
});
