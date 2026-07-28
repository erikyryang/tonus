import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ROUTER_FUTURE } from '@/app/App';
import { AppProviders } from '@/app/providers';
import { AppRoutes } from '@/app/routes';

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function renderApp(route = '/') {
  const queryClient = createTestQueryClient();
  const user = userEvent.setup();

  const result = render(
    <AppProviders queryClient={queryClient}>
      <MemoryRouter initialEntries={[route]} future={ROUTER_FUTURE}>
        <AppRoutes />
      </MemoryRouter>
    </AppProviders>,
  );

  return { ...result, queryClient, user };
}
