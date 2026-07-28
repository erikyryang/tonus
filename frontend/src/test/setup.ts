import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  resolveRelativeUrls();
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});

/**
 * jsdom não implementa fetch; o global vem do Node e exige URL absoluta.
 * O apiClient usa baseURL relativa (/api) por decisão de arquitetura, então
 * o ambiente de teste resolve o caminho contra a origem da página — é um
 * detalhe do jsdom, não do código de produção.
 */
function resolveRelativeUrls() {
  const currentFetch = globalThis.fetch;
  globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
    if (typeof input === 'string' && input.startsWith('/')) {
      return currentFetch(new URL(input, window.location.origin), init);
    }
    return currentFetch(input, init);
  }) as typeof fetch;
}
