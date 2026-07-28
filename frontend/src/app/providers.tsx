import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useState } from 'react';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        // 401 é tratado pelo apiClient (refresh + retry); repetir aqui não ajuda.
        retry: 1,
      },
    },
  });
}

export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [fallbackClient] = useState(createQueryClient);
  return <QueryClientProvider client={queryClient ?? fallbackClient}>{children}</QueryClientProvider>;
}
