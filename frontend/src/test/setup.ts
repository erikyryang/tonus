import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { installLocalStorage } from './localStorage';
import { installMatchMedia } from './matchMedia';
import { server } from './server';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  resolveRelativeUrls();
});

/**
 * Preferência de sistema clara por padrão: o teste que se importa com o tema
 * reinstala o stub com o valor que quer. Sem isso, todo teste que renderiza a
 * aplicação quebraria ao montar o ThemeProvider.
 */
beforeEach(() => {
  installLocalStorage();
  installMatchMedia(false);
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // A classe do tema vive no <html>, fora da árvore que o cleanup desmonta —
  // sem remover, um teste escuro contamina o próximo.
  document.documentElement.classList.remove('dark');
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
