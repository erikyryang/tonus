import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '@/lib/apiClient';
import { type Role, type SessionUser, authApi, sessionQueryKey } from './api/authApi';

/** Default destination for each role — used in login, at the root and in the guards. */
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
    // 401 is already handled by apiClient (refresh + retry). Insisting here would only
    // multiply the call.
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
 * Separates "I don't know who you are" from "I couldn't ask".
 *
 * The difference changes what the user sees: 401 means the session is actually over and the
 * destination is login; API down or 500 is a temporary failure, and sending to
 * login in that case would end a session that's still valid — the user
 * would type the password for nothing and still wouldn't get through.
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
      // Dirty cache can't leak into the next login.
      queryClient.clear();
      navigate('/login', { replace: true });
    },
  });
}
