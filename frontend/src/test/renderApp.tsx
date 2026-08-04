import { QueryClient, type QueryObserverOptions } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AppProviders } from '@/app/providers';
import { AppRoutes } from '@/app/routes';

export function createTestQueryClient(queryOverrides: Partial<QueryObserverOptions> = {}) {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, ...queryOverrides },
      mutations: { retry: false },
    },
  });
}

/**
 * O cliente é injetável porque um teste de invalidação precisa distinguir
 * "refez a chamada porque foi invalidada" de "refez porque tudo é stale por
 * padrão" — e isso se faz com staleTime próprio, não mockando o Query.
 */
export function renderApp(route = '/', queryClient = createTestQueryClient()) {
  const user = userEvent.setup();

  const result = render(
    <AppProviders queryClient={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AppRoutes />
      </MemoryRouter>
    </AppProviders>,
  );

  return { ...result, queryClient, user };
}
