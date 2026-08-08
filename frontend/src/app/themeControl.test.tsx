import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ADMIN, STUDENT, TEACHER, meOk } from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { anyDayEmpty, anyMyGridEmpty, anyMyScheduleEmpty } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

/**
 * Aluno e professor usam a aplicação à noite, no celular — o controle não pode
 * existir só no shell administrativo.
 */
describe('controle de tema nos shells', () => {
  it('aparece no shell do ADMIN', async () => {
    server.use(meOk(ADMIN), anyDayEmpty());
    renderApp('/admin/schedule');

    expect(await screen.findByRole('group', { name: 'Tema' })).toBeInTheDocument();
  });

  it('aparece no shell do STUDENT', async () => {
    server.use(meOk(STUDENT), anyMyScheduleEmpty());
    renderApp('/me/schedule');

    expect(await screen.findByRole('group', { name: 'Tema' })).toBeInTheDocument();
  });

  it('aparece no shell do TEACHER', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());
    renderApp('/me/grid');

    expect(await screen.findByRole('group', { name: 'Tema' })).toBeInTheDocument();
  });

  it('mostra qual dos três estados está ativo, com rótulo textual', async () => {
    server.use(meOk(ADMIN), anyDayEmpty());
    renderApp('/admin/schedule');

    const group = await screen.findByRole('group', { name: 'Tema' });
    // Ícone sozinho não diz qual estado está ativo: o nome acessível de cada
    // opção vem do rótulo em texto.
    expect(within(group).getByRole('button', { name: 'Claro' })).toHaveAttribute('aria-pressed', 'false');
    expect(within(group).getByRole('button', { name: 'Escuro' })).toHaveAttribute('aria-pressed', 'false');
    expect(within(group).getByRole('button', { name: 'Sistema' })).toHaveAttribute('aria-pressed', 'true');
  });
});
