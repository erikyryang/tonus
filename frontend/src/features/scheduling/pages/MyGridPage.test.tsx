import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { addDays, todayIsoDate } from '@/lib/dates';
import { STUDENT, TEACHER, meOk } from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { anyMyScheduleEmpty, grid, myGridHandler } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

const TODAY = todayIsoDate();

const MORNING = {
  time: '07:00',
  students: [
    { studentId: 1, studentName: 'Ana Alves' },
    { studentId: 2, studentName: 'Bia Barros' },
  ],
};

describe('minha grade (professor)', () => {
  it('mostra o dia agrupado por hora com os alunos', async () => {
    server.use(meOk(TEACHER), myGridHandler((date) => grid(date, date === TODAY ? [MORNING] : [])));

    renderApp('/me/grid');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
    const block = await screen.findByRole('region', { name: 'Aulas das 07:00' });
    expect(within(block).getByText('Ana Alves')).toBeInTheDocument();
    expect(within(block).getByText('Bia Barros')).toBeInTheDocument();
  });

  /** Mesma navegação de data da SPEC-007 — e a data escolhida chega ao backend. */
  it('navega para o próximo dia', async () => {
    const asked: string[] = [];
    server.use(meOk(TEACHER), myGridHandler((date) => grid(date), asked));

    const { user } = renderApp('/me/grid');

    await waitFor(() => expect(asked).toEqual([TODAY]));

    await user.click(screen.getByRole('button', { name: 'Próximo dia' }));

    await waitFor(() => expect(asked).toEqual([TODAY, addDays(TODAY, 1)]));
  });

  /** A grade do professor é leitura: cancelar e remarcar são do ADMIN. */
  it('não oferece ação nenhuma sobre a aula', async () => {
    server.use(meOk(TEACHER), myGridHandler((date) => grid(date, [MORNING])));

    renderApp('/me/grid');

    await screen.findByText('Ana Alves');
    expect(screen.queryByRole('button', { name: /Ações de/ })).not.toBeInTheDocument();
  });

  it('mostra vazio explícito no dia sem aula', async () => {
    server.use(meOk(TEACHER), myGridHandler((date) => grid(date)));

    renderApp('/me/grid');

    expect(await screen.findByText(/nenhuma aula neste dia/i)).toBeInTheDocument();
  });

  /** Guard do papel: a grade é do professor. */
  it('manda o aluno para a própria agenda', async () => {
    server.use(meOk(STUDENT), anyMyScheduleEmpty());

    renderApp('/me/grid');

    expect(await screen.findByRole('heading', { name: 'Minha agenda' })).toBeInTheDocument();
  });
});
