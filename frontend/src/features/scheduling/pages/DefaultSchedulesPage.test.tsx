import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import type { DefaultSchedule, DefaultScheduleInput } from '@/features/scheduling/api/schedulingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { pageOf } from '@/test/pageOf';
import { student, studentsList } from '@/test/registrationHandlers';
import { createTestQueryClient, renderApp } from '@/test/renderApp';
import {
  createDefaultScheduleOk,
  defaultSchedule,
  defaultSchedulesHandler,
  defaultSchedulesList,
  deleteDefaultScheduleOk,
  problem,
  updateDefaultScheduleOk,
} from '@/test/schedulingHandlers';
import { server } from '@/test/server';

const ANA = student({ id: 1, name: 'Ana Alves' });
const BIA = student({ id: 2, name: 'Bia Barros', active: false });

describe('lista de horários padrão', () => {
  it('mostra o aluno e a hora', async () => {
    server.use(meOk(ADMIN), studentsList(ANA), defaultSchedulesList(defaultSchedule({ id: 5, studentId: 1, time: '07:00' })));

    renderApp('/admin/default-schedules');

    const row = await screen.findByRole('row', { name: /Ana Alves/ });
    expect(within(row).getByText('07:00')).toBeInTheDocument();
  });
});

describe('cadastro de horário padrão', () => {
  it('cria o horário e a lista volta com ele', async () => {
    const sent: DefaultScheduleInput[] = [];
    const schedules: DefaultSchedule[] = [];

    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      defaultSchedulesHandler(() => pageOf(schedules)),
      createDefaultScheduleOk((body) => {
        sent.push(body);
        schedules.push(defaultSchedule({ id: 90, ...body }));
      }),
    );

    const { user } = renderApp('/admin/default-schedules');

    await user.click(await screen.findByRole('button', { name: 'Novo horário' }));
    const dialog = await screen.findByRole('dialog');

    await user.selectOptions(within(dialog).getByLabelText('Aluno'), '1');
    await user.type(within(dialog).getByLabelText('Hora'), '07:00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ studentId: 1, time: '07:00' }]));
    expect(await screen.findByRole('row', { name: /Ana Alves/ })).toBeInTheDocument();
  });

  /** Critério de aceite: aluno inativo não aparece no select. */
  it('o select de aluno só oferece ativos', async () => {
    server.use(meOk(ADMIN), studentsList(ANA, BIA), defaultSchedulesList());

    const { user } = renderApp('/admin/default-schedules');

    await user.click(await screen.findByRole('button', { name: 'Novo horário' }));
    const select = within(await screen.findByRole('dialog')).getByLabelText('Aluno');

    expect(within(select).getByRole('option', { name: 'Ana Alves' })).toBeInTheDocument();
    expect(within(select).queryByRole('option', { name: 'Bia Barros' })).not.toBeInTheDocument();
  });
});

describe('edição de horário padrão', () => {
  it('abre com aluno e hora atuais e envia a hora nova', async () => {
    const sent: Array<{ id: number; body: DefaultScheduleInput }> = [];
    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      defaultSchedulesList(defaultSchedule({ id: 5, studentId: 1, time: '07:00' })),
      updateDefaultScheduleOk((id, body) => sent.push({ id, body })),
    );

    const { user } = renderApp('/admin/default-schedules');

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Editar' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText('Hora')).toHaveValue('07:00');

    await user.clear(within(dialog).getByLabelText('Hora'));
    await user.type(within(dialog).getByLabelText('Hora'), '09:00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(sent).toEqual([{ id: 5, body: { studentId: 1, time: '09:00' } }]));
  });

  /**
   * 409 é "esse aluno já tem horário" — a lista na tela envelheceu. A mensagem
   * é a do back, e a listagem é refeita para mostrar o horário que já existe.
   */
  it('explica o 409 com a mensagem do back e refaz a lista', async () => {
    let createdByOtherAdmin = false;
    const requests: string[] = [];

    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      defaultSchedulesHandler(
        () =>
          pageOf(createdByOtherAdmin ? [defaultSchedule({ id: 5, studentId: 1, time: '07:00' })] : []),
        requests,
      ),
      http.post('/api/default-schedules', () => {
        createdByOtherAdmin = true;
        return problem(409, 'Conflict', 'Aluno já tem horário padrão: 1');
      }),
    );

    // staleTime infinito: uma segunda chamada só pode vir de invalidação.
    const { user } = renderApp('/admin/default-schedules', createTestQueryClient({ staleTime: Infinity }));

    await user.click(await screen.findByRole('button', { name: 'Novo horário' }));
    const dialog = await screen.findByRole('dialog');

    await user.selectOptions(within(dialog).getByLabelText('Aluno'), '1');
    await user.type(within(dialog).getByLabelText('Hora'), '07:00');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText('Aluno já tem horário padrão: 1')).toBeInTheDocument();
    await waitFor(() => expect(requests.length).toBeGreaterThan(1));
    expect(await screen.findByRole('row', { name: /Ana Alves/ })).toBeInTheDocument();
  });
});

describe('exclusão de horário padrão', () => {
  /**
   * Decisão da SPEC-003 refletida na UI: apagar o horário apaga junto todas as
   * exceções do aluno, cancelamentos e remarcações. Quem confirma precisa saber
   * o que mais vai embora.
   */
  it('avisa que as exceções do aluno vão junto', async () => {
    const deleted: number[] = [];
    server.use(
      meOk(ADMIN),
      studentsList(ANA),
      defaultSchedulesList(defaultSchedule({ id: 5, studentId: 1, time: '07:00' })),
      deleteDefaultScheduleOk((id) => deleted.push(id)),
    );

    const { user } = renderApp('/admin/default-schedules');

    await user.click(await screen.findByRole('button', { name: 'Ações de Ana Alves' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Excluir' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/exceções deste aluno serão apagadas junto/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Excluir' }));

    await waitFor(() => expect(deleted).toEqual([5]));
  });
});
