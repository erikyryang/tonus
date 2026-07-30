import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ADMIN, STUDENT, TEACHER, meOk, meUnauthorized, refreshFails } from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { anyDayEmpty, anyMyGridEmpty, anyMyScheduleEmpty } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

describe('guards de rota por papel', () => {
  it('manda o STUDENT que tenta rota de ADMIN para a própria agenda', async () => {
    server.use(meOk(STUDENT), anyMyScheduleEmpty());
    renderApp('/admin/students');

    expect(await screen.findByRole('heading', { name: 'Minha agenda' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Alunos' })).not.toBeInTheDocument();
  });

  it('manda o TEACHER que tenta rota de ADMIN para a própria grade', async () => {
    server.use(meOk(TEACHER), anyMyGridEmpty());
    renderApp('/admin/invoices');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
  });

  it('manda o ADMIN que tenta rota de aluno para o shell administrativo', async () => {
    server.use(meOk(ADMIN), anyDayEmpty());
    renderApp('/me/schedule');

    expect(await screen.findByRole('link', { name: 'Alunos' })).toBeInTheDocument();
  });

  it('manda anônimo para o login, não para página de erro', async () => {
    server.use(meUnauthorized(), refreshFails());
    renderApp('/admin/students');

    expect(await screen.findByRole('button', { name: /entrar/i })).toBeInTheDocument();
    expect(screen.queryByText(/erro/i)).not.toBeInTheDocument();
  });

  it('resolve a raiz pelo destino do papel', async () => {
    server.use(meOk(ADMIN), anyDayEmpty());
    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Agenda' })).toBeInTheDocument();
  });
});
