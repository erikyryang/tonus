import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { DaySchedule, ScheduleExceptionRequest } from '@/features/scheduling/api/schedulingApi';
import { ADMIN, meOk } from '@/test/authHandlers';
import { createTestQueryClient, renderApp } from '@/test/renderApp';
import {
  HOLIDAY,
  MONDAY,
  SATURDAY,
  THURSDAY,
  WEDNESDAY,
  cancellation,
  createExceptionBadRequest,
  createExceptionOk,
  day,
  deleteExceptionOk,
  dayScheduleHandler,
  entry,
} from '@/test/schedulingHandlers';
import { server } from '@/test/server';

const ANA = { studentId: 1, studentName: 'Ana Alves', teacherId: 10, teacherName: 'Bruno Braga' };
const BIA = { studentId: 2, studentName: 'Bia Barros', teacherId: 10, teacherName: 'Bruno Braga' };
const CAIO = { studentId: 3, studentName: 'Caio Costa', teacherId: 11, teacherName: 'Dora Dias' };
const DUDA = { studentId: 4, studentName: 'Duda Dantas', teacherId: 11, teacherName: 'Dora Dias' };

function renderSchedule(date: string, queryClient = createTestQueryClient()) {
  return renderApp(`/admin/schedule?date=${date}`, queryClient);
}

function hourBlock(time: string) {
  return screen.findByRole('region', { name: `Aulas das ${time}` });
}

describe('agenda do dia', () => {
  it('agrupa as aulas por hora e, dentro da hora, por professor', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        day(WEDNESDAY, {
          entries: [
            entry({ ...ANA, time: '07:00' }),
            entry({ ...BIA, time: '07:00' }),
            entry({ ...CAIO, time: '07:00' }),
            entry({ ...DUDA, time: '18:00' }),
          ],
        }),
      ),
    );

    renderSchedule(WEDNESDAY);

    const morning = await hourBlock('07:00');
    const bruno = within(morning).getByRole('group', { name: 'Bruno Braga' });
    expect(within(bruno).getByText('Ana Alves')).toBeInTheDocument();
    expect(within(bruno).getByText('Bia Barros')).toBeInTheDocument();
    expect(within(bruno).queryByText('Caio Costa')).not.toBeInTheDocument();

    const dora = within(morning).getByRole('group', { name: 'Dora Dias' });
    expect(within(dora).getByText('Caio Costa')).toBeInTheDocument();

    const evening = await hourBlock('18:00');
    expect(within(evening).getByRole('group', { name: 'Dora Dias' })).toBeInTheDocument();
    expect(within(evening).getByText('Duda Dantas')).toBeInTheDocument();

    // Só as horas com aula viram bloco.
    expect(screen.getAllByRole('region')).toHaveLength(2);
  });

  it('mostra o banner do feriado e nenhum grupo', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(HOLIDAY, { holiday: 'Independência do Brasil' })),
    );

    renderSchedule(HOLIDAY);

    expect(await screen.findByText(/Independência do Brasil/)).toBeInTheDocument();
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    expect(screen.queryByText(/nenhuma aula neste dia/i)).not.toBeInTheDocument();
  });

  it('avisa que não há aula no fim de semana', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(SATURDAY)),
    );

    renderSchedule(SATURDAY);

    expect(await screen.findByText(/sem aulas aos fins de semana/i)).toBeInTheDocument();
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
  });

  it('mostra vazio explícito em dia útil sem aula', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY)),
    );

    renderSchedule(WEDNESDAY);

    expect(await screen.findByText(/nenhuma aula neste dia/i)).toBeInTheDocument();
  });

  it('marca com badge o aluno remarcado para este dia', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        day(WEDNESDAY, {
          entries: [
            entry({ ...ANA, time: '09:00', origin: 'RESCHEDULED', originDate: MONDAY, exceptionId: 77 }),
          ],
        }),
      ),
    );

    renderSchedule(WEDNESDAY);

    expect(await screen.findByText('remarcada de 27/07')).toBeInTheDocument();
  });
});

describe('menu de ações', () => {
  /**
   * A API não comporta cancelar nem remarcar uma aula que já é remarcação: a
   * exceção é indexada pela data de origem, e uma segunda exceção no dia de
   * destino não tiraria a aula da tela. Melhor não oferecer do que oferecer um
   * item que dispara WhatsApp e não muda nada.
   */
  it('oferece só desfazer numa aula que veio de remarcação', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        day(WEDNESDAY, {
          entries: [entry({ ...ANA, time: '09:00', origin: 'RESCHEDULED', originDate: MONDAY, exceptionId: 77 })],
        }),
      ),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));

    const menu = await screen.findByRole('menu', { name: /^Ações de Ana Alves/ });
    expect(within(menu).getByRole('menuitem', { name: 'Desfazer' })).toBeInTheDocument();
    expect(within(menu).queryByRole('menuitem', { name: 'Cancelar' })).not.toBeInTheDocument();
    expect(within(menu).queryByRole('menuitem', { name: 'Remarcar' })).not.toBeInTheDocument();
  });

  /**
   * Visto na validação visual contra o backend real: remarcar para um dia útil
   * deixa o aluno DUAS vezes naquele dia, e pode ser na mesma hora e com o mesmo
   * professor. Na tela a badge distingue; no leitor de tela, só o rótulo.
   */
  it('distingue as duas aulas do mesmo aluno na mesma hora', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        day(WEDNESDAY, {
          entries: [
            entry({ ...ANA, time: '09:00' }),
            entry({ ...ANA, time: '09:00', origin: 'RESCHEDULED', originDate: MONDAY, exceptionId: 77 }),
          ],
        }),
      ),
    );

    renderSchedule(WEDNESDAY);

    expect(await screen.findByRole('button', { name: 'Ações de Ana Alves às 09:00' })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Ações de Ana Alves às 09:00, remarcada de 27/07' }),
    ).toBeInTheDocument();
  });

  it('oferece cancelar e remarcar numa aula do horário padrão', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));

    const menu = await screen.findByRole('menu', { name: /^Ações de Ana Alves/ });
    expect(within(menu).getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'Cancelar',
      'Remarcar',
    ]);
  });
});

describe('diálogo', () => {
  async function openCancelDialog() {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })),
    );

    const { user } = renderSchedule(WEDNESDAY);
    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Cancelar' }));
    await screen.findByRole('dialog');
    return user;
  }

  /** Quem fecha o diálogo tem de voltar para onde estava, não para o topo da página. */
  it('devolve o foco ao botão de ações ao fechar', async () => {
    const user = await openCancelDialog();

    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Voltar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: /^Ações de Ana Alves/ })).toHaveFocus();
  });

  it('fecha no Escape', async () => {
    const user = await openCancelDialog();

    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('abre com o foco no primeiro campo, não na ação destrutiva', async () => {
    await openCancelDialog();

    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Voltar' })).toHaveFocus();
  });
});

describe('cancelar aula', () => {
  it('confirma avisando o efeito, envia a exceção e atualiza a lista', async () => {
    let canceled = false;
    const sent: ScheduleExceptionRequest[] = [];

    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        canceled
          ? day(WEDNESDAY, {
              entries: [entry({ ...BIA, time: '07:00' })],
              cancellations: [cancellation({ ...ANA, exceptionId: 55, time: '07:00' })],
            })
          : day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' }), entry({ ...BIA, time: '07:00' })] }),
      ),
      createExceptionOk((body) => {
        sent.push(body);
        canceled = true;
      }, 55),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Cancelar' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/O aluno será avisado por WhatsApp\./)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar aula' }));

    await waitFor(() =>
      expect(sent).toEqual([{ studentId: 1, date: WEDNESDAY, type: 'CANCELED' }]),
    );

    const cancellations = await screen.findByRole('region', { name: 'Cancelamentos' });
    expect(within(cancellations).getByText('Ana Alves')).toBeInTheDocument();

    const morning = await hourBlock('07:00');
    expect(within(morning).queryByText('Ana Alves')).not.toBeInTheDocument();
    expect(within(morning).getByText('Bia Barros')).toBeInTheDocument();

    // A live region é montada sempre; quem espera é o waitFor, não o findBy.
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/aula cancelada/i));
  });

  /** Sem desfazer, corrigir um cancelamento marcado por engano exigiria o banco. */
  it('desfaz a exceção e devolve o dia ao template', async () => {
    let undone = false;
    const deleted: string[] = [];

    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() =>
        undone
          ? day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })
          : day(WEDNESDAY, { cancellations: [cancellation({ ...ANA, exceptionId: 55, time: '07:00' })] }),
      ),
      deleteExceptionOk((id) => {
        deleted.push(id);
        undone = true;
      }),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desfazer' }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Desfazer' }));

    await waitFor(() => expect(deleted).toEqual(['55']));

    const morning = await hourBlock('07:00');
    expect(within(morning).getByText('Ana Alves')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Cancelamentos' })).not.toBeInTheDocument();
  });
});

describe('remarcar aula', () => {
  /** O caso comum é trocar o dia e manter a hora — digitá-la de novo é atrito à toa. */
  it('já vem com a hora atual da aula preenchida', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Remarcar' }));

    expect(within(await screen.findByRole('dialog')).getByLabelText('Nova hora')).toHaveValue('07:00');
  });

  it('mostra a mensagem do backend quando a nova data é feriado', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })),
      createExceptionBadRequest('Não é possível remarcar para um feriado: 2026-09-07'),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Remarcar' }));

    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Nova data'), { target: { value: HOLIDAY } });
    fireEvent.change(within(dialog).getByLabelText('Nova hora'), { target: { value: '09:00' } });
    await user.click(within(dialog).getByRole('button', { name: 'Remarcar' }));

    expect(
      await within(dialog).findByText('Não é possível remarcar para um feriado: 2026-09-07'),
    ).toBeInTheDocument();
  });

  it('recusa data no passado sem chamar a API', async () => {
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(() => day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] })),
    );

    const { user } = renderSchedule(WEDNESDAY);

    await user.click(await screen.findByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Remarcar' }));

    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Nova data'), { target: { value: '2020-01-02' } });
    fireEvent.change(within(dialog).getByLabelText('Nova hora'), { target: { value: '09:00' } });
    await user.click(within(dialog).getByRole('button', { name: 'Remarcar' }));

    // Sem handler de POST: se a chamada saísse, o MSW derrubaria o teste.
    expect(await within(dialog).findByText(/não é possível remarcar para uma data passada/i)).toBeInTheDocument();
  });

  /**
   * A aula sai de um dia e entra em outro: as duas agendas mudam. Invalidar só
   * a que está na tela deixaria o dia de destino desatualizado em cache.
   */
  it('invalida o dia de origem e o de destino', async () => {
    const requested: string[] = [];
    let rescheduled = false;

    const resolve = (date: string): DaySchedule => {
      if (date === WEDNESDAY) {
        return rescheduled ? day(WEDNESDAY) : day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] });
      }
      if (date === THURSDAY) {
        return rescheduled
          ? day(THURSDAY, {
              entries: [
                entry({ ...ANA, time: '09:00', origin: 'RESCHEDULED', originDate: WEDNESDAY, exceptionId: 88 }),
              ],
            })
          : day(THURSDAY);
      }
      return day(date);
    };

    server.use(meOk(ADMIN), dayScheduleHandler(resolve, requested), createExceptionOk(() => {
      rescheduled = true;
    }, 88));

    // staleTime infinito: assim uma chamada repetida só pode vir de invalidação.
    const { user } = renderSchedule(WEDNESDAY, createTestQueryClient({ staleTime: Infinity, gcTime: Infinity }));

    await screen.findByText('Ana Alves');
    await user.click(screen.getByRole('button', { name: 'Próximo dia' }));
    await screen.findByText(/nenhuma aula neste dia/i);
    await user.click(screen.getByRole('button', { name: 'Dia anterior' }));
    await screen.findByText('Ana Alves');

    expect(requested).toEqual([WEDNESDAY, THURSDAY]);

    await user.click(screen.getByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Remarcar' }));

    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Nova data'), { target: { value: THURSDAY } });
    fireEvent.change(within(dialog).getByLabelText('Nova hora'), { target: { value: '09:00' } });
    await user.click(within(dialog).getByRole('button', { name: 'Remarcar' }));

    // Origem está na tela: invalidar já refaz a chamada.
    await waitFor(() => expect(requested).toEqual([WEDNESDAY, THURSDAY, WEDNESDAY]));
    await screen.findByText(/nenhuma aula neste dia/i);

    // Destino estava em cache e fresco; só volta à API por ter sido invalidado.
    await user.click(screen.getByRole('button', { name: 'Próximo dia' }));
    await waitFor(() => expect(requested).toEqual([WEDNESDAY, THURSDAY, WEDNESDAY, THURSDAY]));
    expect(await screen.findByText('remarcada de 29/07')).toBeInTheDocument();
  });

  /** Desfazer devolve a aula ao dia de origem: as duas agendas mudam de novo. */
  it('desfazer a remarcação invalida destino e origem', async () => {
    const requested: string[] = [];
    let undone = false;

    const resolve = (date: string): DaySchedule => {
      if (date === THURSDAY) {
        return undone
          ? day(THURSDAY)
          : day(THURSDAY, {
              entries: [
                entry({ ...ANA, time: '09:00', origin: 'RESCHEDULED', originDate: WEDNESDAY, exceptionId: 88 }),
              ],
            });
      }
      if (date === WEDNESDAY) {
        return undone ? day(WEDNESDAY, { entries: [entry({ ...ANA, time: '07:00' })] }) : day(WEDNESDAY);
      }
      return day(date);
    };

    const deleted: string[] = [];
    server.use(
      meOk(ADMIN),
      dayScheduleHandler(resolve, requested),
      deleteExceptionOk((id) => {
        deleted.push(id);
        undone = true;
      }),
    );

    const { user } = renderSchedule(THURSDAY, createTestQueryClient({ staleTime: Infinity, gcTime: Infinity }));

    await screen.findByText('remarcada de 29/07');
    await user.click(screen.getByRole('button', { name: 'Dia anterior' }));
    await screen.findByText(/nenhuma aula neste dia/i);
    await user.click(screen.getByRole('button', { name: 'Próximo dia' }));
    await screen.findByText('remarcada de 29/07');

    expect(requested).toEqual([THURSDAY, WEDNESDAY]);

    await user.click(screen.getByRole('button', { name: /^Ações de Ana Alves/ }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desfazer' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Desfazer' }));

    await waitFor(() => expect(deleted).toEqual(['88']));
    await waitFor(() => expect(requested).toEqual([THURSDAY, WEDNESDAY, THURSDAY]));

    await user.click(screen.getByRole('button', { name: 'Dia anterior' }));
    await waitFor(() => expect(requested).toEqual([THURSDAY, WEDNESDAY, THURSDAY, WEDNESDAY]));
    expect(await screen.findByText('Ana Alves')).toBeInTheDocument();
  });
});
