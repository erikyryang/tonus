import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Holiday, HolidayInput } from '@/features/scheduling/api/schedulingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { pageOf } from '@/test/pageOf';
import { renderApp } from '@/test/renderApp';
import {
  createHolidayOk,
  deleteHolidayOk,
  holiday,
  holidaysHandler,
  holidaysList,
  problem,
} from '@/test/schedulingHandlers';
import { server } from '@/test/server';

const INDEPENDENCE = holiday({ id: 7, date: '2026-09-07', description: 'Independência' });

describe('lista de feriados', () => {
  it('mostra data e descrição', async () => {
    server.use(meOk(ADMIN), holidaysList(INDEPENDENCE));

    renderApp('/admin/holidays');

    const row = await screen.findByRole('row', { name: /Independência/ });
    expect(within(row).getByText('07/09/2026')).toBeInTheDocument();
  });
});

describe('cadastro de feriado', () => {
  /**
   * Critério de aceite: criar feriado dispara WhatsApp para todos os alunos
   * ativos. O aviso é sobre o efeito real — não há como desavisar depois.
   */
  it('avisa sobre a notificação em massa e cria', async () => {
    const sent: HolidayInput[] = [];
    const holidays: Holiday[] = [];

    server.use(
      meOk(ADMIN),
      holidaysHandler(() => pageOf(holidays)),
      createHolidayOk((body) => {
        sent.push(body);
        holidays.push(holiday({ id: 80, ...body }));
      }),
    );

    const { user } = renderApp('/admin/holidays');

    await user.click(await screen.findByRole('button', { name: 'Novo feriado' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByText(/alunos ativos serão notificados por WhatsApp/i)).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Data'), '2026-09-07');
    await user.type(within(dialog).getByLabelText('Descrição'), 'Independência');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ date: '2026-09-07', description: 'Independência' }]));
    expect(await screen.findByRole('row', { name: /Independência/ })).toBeInTheDocument();
  });
});

describe('feriado já cadastrado', () => {
  /** O back é a autoridade sobre a duplicata; a tela repete a razão dele. */
  it('mostra a mensagem do 409 sem fechar o formulário', async () => {
    server.use(
      meOk(ADMIN),
      holidaysList(),
      http.post('/api/holidays', () => problem(409, 'Conflict', 'Feriado já cadastrado: 2026-09-07')),
    );

    const { user } = renderApp('/admin/holidays');

    await user.click(await screen.findByRole('button', { name: 'Novo feriado' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Data'), '2026-09-07');
    await user.type(within(dialog).getByLabelText('Descrição'), 'Independência');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText('Feriado já cadastrado: 2026-09-07')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Salvar' })).toBeEnabled();
  });
});

describe('exclusão de feriado', () => {
  it('pede confirmação antes de excluir', async () => {
    const deleted: number[] = [];
    server.use(meOk(ADMIN), holidaysList(INDEPENDENCE), deleteHolidayOk((id) => deleted.push(id)));

    const { user } = renderApp('/admin/holidays');

    await user.click(await screen.findByRole('button', { name: 'Ações de Independência' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Excluir' }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(deleted).toEqual([7]));
  });
});
