import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiClient, setUnauthorizedHandler } from '@/lib/apiClient';
import { unauthorized } from '@/test/authHandlers';
import { server } from '@/test/server';

describe('apiClient', () => {
  beforeEach(() => {
    setUnauthorizedHandler(() => {});
  });

  it('faz uma única tentativa de refresh e repete a chamada original', async () => {
    const refresh = vi.fn();
    let calls = 0;
    server.use(
      http.get('/api/students', () => {
        calls += 1;
        return calls === 1 ? unauthorized('expirado') : HttpResponse.json({ content: [] });
      }),
      http.post('/api/auth/refresh', () => {
        refresh();
        return new HttpResponse(null, { status: 200 });
      }),
    );

    await expect(apiClient.get('/students')).resolves.toEqual({ content: [] });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(calls).toBe(2);
  });

  it('compartilha UMA promise de refresh entre requisições concorrentes com 401', async () => {
    const refresh = vi.fn();
    const seen = new Set<string>();
    server.use(
      http.get('/api/:resource', ({ params }) => {
        const resource = String(params.resource);
        if (!seen.has(resource)) {
          seen.add(resource);
          return unauthorized('expirado');
        }
        return HttpResponse.json({ resource });
      }),
      http.post('/api/auth/refresh', async () => {
        refresh();
        await new Promise((resolve) => setTimeout(resolve, 10));
        return new HttpResponse(null, { status: 200 });
      }),
    );

    const results = await Promise.all([
      apiClient.get('/students'),
      apiClient.get('/teachers'),
      apiClient.get('/invoices'),
    ]);

    expect(results).toEqual([{ resource: 'students' }, { resource: 'teachers' }, { resource: 'invoices' }]);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('não tenta refresh mais de uma vez: falhou, aciona o handler e propaga o 401', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    const refresh = vi.fn();
    server.use(
      http.get('/api/students', () => unauthorized('expirado')),
      http.post('/api/auth/refresh', () => {
        refresh();
        return unauthorized('refresh inválido');
      }),
    );

    await expect(apiClient.get('/students')).rejects.toBeInstanceOf(ApiError);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('expõe o detail do Problem Details no erro', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Login já usado' },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(apiClient.post('/auth/login', { login: 'ana', password: 'x' })).rejects.toMatchObject({
      status: 400,
      detail: 'Login já usado',
    });
  });
});
