import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '@/lib/apiClient';
import { type Role, type SessionUser, authApi, sessionQueryKey } from './api/authApi';

/** Destino padrão de cada papel — usado no login, na raiz e nos guards. */
const DESTINATION: Record<Role, string> = {
  ADMIN: '/admin/schedule',
  STUDENT: '/me/schedule',
  TEACHER: '/me/grid',
};

export function destinationFor(role: Role): string {
  return DESTINATION[role];
}

export function useSession() {
  return useQuery<SessionUser>({
    queryKey: sessionQueryKey,
    queryFn: authApi.me,
    // 401 já é tratado pelo apiClient (refresh + retry). Insistir aqui só
    // multiplicaria a chamada.
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export type SessionState =
  | { status: 'loading' }
  | { status: 'unauthenticated' }
  | { status: 'error'; retry: () => void }
  | { status: 'ready'; user: SessionUser };

/**
 * Separa "não sei quem você é" de "não consegui perguntar".
 *
 * A diferença muda o que o usuário vê: 401 é sessão de fato encerrada e o
 * destino é o login; API fora do ar ou 500 é falha temporária, e mandar para o
 * login nesse caso encerraria uma sessão que continua válida — o usuário
 * digitaria a senha à toa e ainda assim não passaria.
 */
export function useSessionState(): SessionState {
  const { data, error, isPending, refetch } = useSession();

  if (isPending) return { status: 'loading' };
  if (data) return { status: 'ready', user: data };
  if (error instanceof ApiError && error.status === 401) return { status: 'unauthenticated' };
  return { status: 'error', retry: () => void refetch() };
}

export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      // Cache sujo não pode vazar para o próximo login.
      queryClient.clear();
      navigate('/login', { replace: true });
    },
  });
}
