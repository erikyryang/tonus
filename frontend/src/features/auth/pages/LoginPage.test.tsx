import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ADMIN, STUDENT, loginInvalid, loginOk, meOk, meUnauthorized } from '@/test/authHandlers';
import { renderApp } from '@/test/renderApp';
import { anyDayEmpty, anyMyScheduleEmpty } from '@/test/schedulingHandlers';
import { server } from '@/test/server';

async function submitLogin(user: ReturnType<typeof renderApp>['user']) {
  await user.type(screen.getByLabelText(/login/i), 'ana');
  await user.type(screen.getByLabelText(/senha/i), 'segredo123');
  await user.click(screen.getByRole('button', { name: /entrar/i }));
}

describe('LoginPage', () => {
  it('mostra a mesma mensagem para credencial inválida, sem distinguir usuário de senha', async () => {
    server.use(loginInvalid(), meUnauthorized());
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByText('Login ou senha inválidos.')).toBeInTheDocument();
    expect(screen.queryByText(/não encontrad/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/senha incorreta/i)).not.toBeInTheDocument();
  });

  it('valida o formulário antes de chamar a API', async () => {
    server.use(meUnauthorized());
    const { user } = renderApp('/login');

    await user.click(screen.getByRole('button', { name: /entrar/i }));

    expect(await screen.findByText('Informe o login.')).toBeInTheDocument();
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument();
  });

  it('leva o ADMIN para o shell administrativo após o login', async () => {
    server.use(loginOk(), meOk(ADMIN), anyDayEmpty());
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByRole('link', { name: 'Alunos' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Professores' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Faturas' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Comunicados' })).toBeInTheDocument();
    expect(screen.getByText('Ana Admin')).toBeInTheDocument();
  });

  it('avisa quando o rate limit do backend corta as tentativas', async () => {
    server.use(http.post('/api/auth/login', () => new HttpResponse(null, { status: 429 })));
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByText(/muitas tentativas/i)).toBeInTheDocument();
  });

  /** An infrastructure error can't turn into "wrong password" in the user's face. */
  it('mostra o motivo do backend quando a falha não é de credencial', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { status: 503, detail: 'Serviço em manutenção' },
          { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByText('Serviço em manutenção')).toBeInTheDocument();
    expect(screen.queryByText('Login ou senha inválidos.')).not.toBeInTheDocument();
  });

  it('avisa quando não consegue falar com a API', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.error()));
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByText(/não foi possível conectar/i)).toBeInTheDocument();
  });

  it('leva o STUDENT para a própria agenda após o login', async () => {
    server.use(loginOk(), meOk(STUDENT), anyMyScheduleEmpty());
    const { user } = renderApp('/login');

    await submitLogin(user);

    expect(await screen.findByRole('heading', { name: 'Minha agenda' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Alunos' })).not.toBeInTheDocument());
  });
});
