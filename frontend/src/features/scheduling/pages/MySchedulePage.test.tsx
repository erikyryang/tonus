import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
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
  it('mostra o feriado que suprime a aula', async () => {
    server.use(meOk(STUDENT), scheduleOf({ [TOMORROW]: day(TOMORROW, { holiday: 'Independência' }) }));

    renderApp('/me/schedule');

    const tomorrow = await screen.findByRole('listitem', { name: new RegExp(formatDate(TOMORROW)) });
    expect(within(tomorrow).getByText(/Independência/)).toBeInTheDocument();
  });

  /** Estado da spec: quem ainda não tem horário não vê uma semana de "sem aula". */
  it('diz quando não há horário cadastrado', async () => {
    server.use(meOk(STUDENT), scheduleOf({}));

    renderApp('/me/schedule');

    expect(await screen.findByText(/ainda não tem horário cadastrado/i)).toBeInTheDocument();
  });

  /**
   * São sete chamadas independentes: uma pode falhar sozinha. Os dias que
   * chegaram continuam na tela, o erro aparece com o "tentar de novo", e a
   * conclusão sobre o conjunto fica suspensa — com um buraco na semana, "você
   * ainda não tem horário" seria palpite.
   */
  it('mostra o que carregou quando um dia da semana falha', async () => {
    server.use(
      meOk(STUDENT),
      http.get('/api/me/schedule', ({ request }) => {
        const date = new URL(request.url).searchParams.get('date') ?? '';
        if (date === TOMORROW) return problem(500, 'Internal Server Error', 'Falhou o dia de amanhã');
        return HttpResponse.json(
          date === TODAY ? day(TODAY, { entries: [entry({ studentId: 10, time: '07:00' })] }) : day(date),
        );
      }),
    );

    renderApp('/me/schedule');

    const today = await screen.findByRole('listitem', { name: new RegExp(formatDate(TODAY)) });
    expect(within(today).getByText('07:00')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('Falhou o dia de amanhã');
    expect(screen.queryByText(/ainda não tem horário cadastrado/i)).not.toBeInTheDocument();
  });

  /** Guard do papel: a agenda do aluno é só do aluno. */
  it('manda o professor para a própria grade', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());

    renderApp('/me/schedule');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
  });
});
