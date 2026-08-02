import { screen, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { addDays, todayIsoDate } from '@/lib/dates';
import { formatDate } from '@/lib/formatters';
import { STUDENT, TEACHER, meOk } from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { anyMyGridEmpty, cancellation, day, entry, myScheduleHandler, problem } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

const TODAY = todayIsoDate();
const TOMORROW = addDays(TODAY, 1);

/** O aluno é sempre o do token: o handler responde pelo dia, nunca por id. */
function scheduleOf(byDate: Record<string, ReturnType<typeof day>>) {
  return myScheduleHandler((date) => byDate[date] ?? day(date));
}

describe('minha agenda (aluno)', () => {
  it('mostra a aula dos próximos dias', async () => {
    server.use(
      meOk(STUDENT),
      scheduleOf({
        [TODAY]: day(TODAY, { entries: [entry({ studentId: 10, time: '07:00' })] }),
        [TOMORROW]: day(TOMORROW, { entries: [entry({ studentId: 10, time: '07:00' })] }),
      }),
    );

    renderApp('/me/schedule');

    expect(await screen.findByRole('heading', { name: 'Minha agenda' })).toBeInTheDocument();
    const today = await screen.findByRole('listitem', { name: new RegExp(formatDate(TODAY)) });
    expect(within(today).getByText('07:00')).toBeInTheDocument();
  });

  /**
   * A remarcação é o que o aluno mais precisa enxergar: é a aula que mudou de
   * lugar, e ele não recebe a agenda por outro caminho além do WhatsApp.
   */
  it('mostra a remarcação e o cancelamento que o afetam', async () => {
    server.use(
      meOk(STUDENT),
      scheduleOf({
        [TODAY]: day(TODAY, {
          cancellations: [cancellation({ studentId: 10, exceptionId: 1, time: '07:00' })],
        }),
        [TOMORROW]: day(TOMORROW, {
          entries: [entry({ studentId: 10, time: '09:00', origin: 'RESCHEDULED', originDate: TODAY })],
        }),
      }),
    );

    renderApp('/me/schedule');

    const today = await screen.findByRole('listitem', { name: new RegExp(formatDate(TODAY)) });
    expect(within(today).getByText(/cancelada/i)).toBeInTheDocument();

    const tomorrow = await screen.findByRole('listitem', { name: new RegExp(formatDate(TOMORROW)) });
    expect(within(tomorrow).getByText(/09:00/)).toBeInTheDocument();
    expect(within(tomorrow).getByText(/remarcada/i)).toBeInTheDocument();
  });

  /** Feriado explica por que o dia está vazio; sem a descrição, o aluno adivinharia. */
  it('mostra o comunicado que suspende a aula', async () => {
    server.use(meOk(STUDENT), scheduleOf({ [TOMORROW]: day(TOMORROW, { announcement: { description: 'Independência', suspendsClasses: true } }) }));

    renderApp('/me/schedule');

    const tomorrow = await screen.findByRole('listitem', { name: new RegExp(formatDate(TOMORROW)) });
    expect(within(tomorrow).getByText(/Independência/)).toBeInTheDocument();
  });

  /**
   * O comunicado que não suspende também é do aluno: ele recebe o WhatsApp, e
   * a agenda é onde ele volta para conferir. Mostrar só os que suspendem
   * deixaria o aviso existindo em um lugar só — o que já passou.
   */
  it('mostra o comunicado que não suspende ao lado da aula', async () => {
    server.use(
      meOk(STUDENT),
      scheduleOf({
        [TOMORROW]: day(TOMORROW, {
          announcement: { description: 'Obra na rua, entrada pelos fundos', suspendsClasses: false },
          entries: [entry({ studentId: 1, time: '07:00' })],
        }),
      }),
    );

    renderApp('/me/schedule');

    const tomorrow = await screen.findByRole('listitem', { name: new RegExp(formatDate(TOMORROW)) });
    expect(within(tomorrow).getByText(/Obra na rua/)).toBeInTheDocument();
    expect(within(tomorrow).getByText('07:00')).toBeInTheDocument();
    expect(within(tomorrow).queryByText(/sem aula/i)).not.toBeInTheDocument();
  });

  /** Estado da spec: quem ainda não tem horário não vê uma semana de "sem aula". */
  it('diz quando não há horário cadastrado', async () => {
    server.use(meOk(STUDENT), scheduleOf({}));

    renderApp('/me/schedule');

    expect(await screen.findByText(/ainda não tem horário cadastrado/i)).toBeInTheDocument();
  });

  /**
   * Semana de recesso: o feriado suprime o dia para todo mundo, então a janela
   * inteira vem vazia mesmo para quem tem horário. Concluir "você não tem
   * horário" daí mandaria o aluno à recepção resolver o que não está quebrado.
   */
  it('não conclui que falta horário quando a janela inteira está suspensa', async () => {
    server.use(
      meOk(STUDENT),
      myScheduleHandler((date) => day(date, { announcement: { description: 'Recesso', suspendsClasses: true } })),
    );

    renderApp('/me/schedule');

    await screen.findAllByText(/Recesso/);
    expect(screen.queryByText(/ainda não tem horário cadastrado/i)).not.toBeInTheDocument();
  });

  /**
   * A aula de hoje foi cancelada e a de amanhã remarcada para hoje: as duas
   * coisas no mesmo dia. Mostrar só a aula apagaria justamente o aviso.
   */
  it('mostra a aula e o cancelamento quando os dois caem no mesmo dia', async () => {
    server.use(
      meOk(STUDENT),
      scheduleOf({
        [TODAY]: day(TODAY, {
          entries: [entry({ studentId: 10, time: '09:00', origin: 'RESCHEDULED', originDate: TOMORROW })],
          cancellations: [cancellation({ studentId: 10, exceptionId: 1, time: '07:00' })],
        }),
      }),
    );

    renderApp('/me/schedule');

    const today = await screen.findByRole('listitem', { name: new RegExp(formatDate(TODAY)) });
    expect(within(today).getByText('09:00')).toBeInTheDocument();
    expect(within(today).getByText(/cancelada/i)).toBeInTheDocument();
  });

  /**
   * A janela vem de uma chamada só, então ou ela chega inteira ou não chega.
   * O que não pode acontecer é a tela concluir alguma coisa sobre a semana que
   * não recebeu: "você ainda não tem horário" seria palpite, não informação.
   */
  it('mostra o erro sem concluir nada quando a janela falha', async () => {
    server.use(
      meOk(STUDENT),
      http.get('/api/me/schedule/week', () => problem(500, 'Internal Server Error', 'Falhou a semana')),
    );

    renderApp('/me/schedule');

    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou a semana');
    expect(screen.queryByText(/ainda não tem horário cadastrado/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  /** Uma chamada para a janela inteira, e não uma por dia. */
  it('pede a semana numa chamada só', async () => {
    const asked: string[] = [];
    server.use(meOk(STUDENT), myScheduleHandler((date) => day(date), asked));

    renderApp('/me/schedule');

    await screen.findByText(/ainda não tem horário cadastrado/i);
    expect(asked).toEqual([`${TODAY}:7`]);
  });

  /** Guard do papel: a agenda do aluno é só do aluno. */
  it('manda o professor para a própria grade', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());

    renderApp('/me/schedule');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
  });
});
