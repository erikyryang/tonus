import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { ThemeProvider } from './theme';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        // 401 is handled by apiClient (refresh + retry); retrying here doesn't help.
        retry: 1,
      },
    },
  });
}

export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [fallbackClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient ?? fallbackClient}>
      <ThemeProvider>{children}</ThemeProvider>
    </QueryClientProvider>
  );
}
