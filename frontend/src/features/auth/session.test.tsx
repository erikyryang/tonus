import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import {
  ADMIN,
  TEACHER,
  logoutOk,
  meOk,
  meUnauthorized,
  refreshFails,
  unauthorized,
} from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('sessão', () => {
  it('restaura o usuário no reload sem passar pelo login', async () => {
    server.use(meOk(TEACHER));
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Minha grade' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /entrar/i })).not.toBeInTheDocument();
    expect(screen.getByText('Caio Professor')).toBeInTheDocument();
  });

  it('faz refresh e repete a chamada de forma transparente quando a sessão expirou', async () => {
    const refresh = vi.fn();
    let meCalls = 0;
    server.use(
      http.get('/api/auth/me', () => {
        meCalls += 1;
        return meCalls === 1 ? unauthorized('Não autenticado') : HttpResponse.json(ADMIN);
      }),
      http.post('/api/auth/refresh', () => {
        refresh();
        return new HttpResponse(null, { status: 200 });
      }),
    );

    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Alunos' })).toBeInTheDocument();
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(meCalls).toBe(2);
    expect(screen.queryByRole('button', { name: /entrar/i })).not.toBeInTheDocument();
  });

  it('limpa o cache e vai para /login quando o refresh também falha', async () => {
    server.use(meUnauthorized(), refreshFails());
    const { queryClient } = renderApp('/');

    expect(await screen.findByRole('button', { name: /entrar/i })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined());
  });

  /**
   * API fora do ar não é sessão encerrada: mandar para o login faria o usuário
   * digitar a senha à toa, contra a mesma API que está fora do ar.
   */
  it('mostra erro com retry quando a falha não é 401, sem derrubar para o login', async () => {
    let attempts = 0;
    server.use(
      http.get('/api/auth/me', () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json({ status: 500, detail: 'Erro interno' }, { status: 500 })
          : HttpResponse.json(ADMIN);
      }),
    );
    const { user } = renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent(/não foi possível carregar sua sessão/i);
    expect(screen.queryByRole('button', { name: /entrar/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /tentar de novo/i }));

    expect(await screen.findByRole('link', { name: 'Alunos' })).toBeInTheDocument();
  });

  it('não tenta refresh quando a falha é 500', async () => {
    const refresh = vi.fn();
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json({ status: 500 }, { status: 500 })),
      http.post('/api/auth/refresh', () => {
        refresh();
        return new HttpResponse(null, { status: 200 });
      }),
    );
    renderApp('/');

    await screen.findByRole('alert');
    expect(refresh).not.toHaveBeenCalled();
  });

  it('no logout limpa todo o cache do Query e volta para o login', async () => {
    server.use(meOk(ADMIN), logoutOk(), meUnauthorized(), refreshFails());
    const { user, queryClient } = renderApp('/');

    await screen.findByRole('link', { name: 'Alunos' });
    queryClient.setQueryData(['scheduling', 'day', '2026-07-28'], ['aula do admin anterior']);

    await user.click(screen.getByRole('button', { name: /sair/i }));

    expect(await screen.findByRole('button', { name: /entrar/i })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.getQueryCache().getAll()).toHaveLength(0));
  });
});
