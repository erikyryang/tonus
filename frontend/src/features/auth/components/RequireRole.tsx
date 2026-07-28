import { Navigate, Outlet, useOutletContext } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import type { Role, SessionUser } from '../api/authApi';
import { destinationFor, useSessionState } from '../session';

export interface SessionContext {
  user: SessionUser;
}

/** Nas telas abaixo do guard o usuário já está resolvido — sem query extra. */
export function useSessionUser(): SessionUser {
  return useOutletContext<SessionContext>().user;
}

export function RequireRole({ allow }: { allow: Role[] }) {
  const session = useSessionState();

  if (session.status === 'loading') return <LoadingScreen />;
  if (session.status === 'unauthenticated') return <Navigate to="/login" replace />;
  if (session.status === 'error') return <SessionErrorScreen onRetry={session.retry} />;
  // Papel sem acesso vai para o próprio destino, não para página de erro.
  if (!allow.includes(session.user.role)) return <Navigate to={destinationFor(session.user.role)} replace />;

  return <Outlet context={{ user: session.user } satisfies SessionContext} />;
}

export function LoadingScreen() {
  return (
    <div role="status" aria-live="polite" className="flex min-h-screen items-center justify-center text-slate-500">
      Carregando…
    </div>
  );
}

/**
 * Falha ao consultar a sessão que não é 401. Não manda para o login de
 * propósito: a sessão pode estar viva e o problema ser só a API fora do ar.
 */
export function SessionErrorScreen({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex min-h-screen flex-col items-center justify-center gap-4 p-4 text-center text-slate-700"
    >
      <p>Não foi possível carregar sua sessão.</p>
      <Button variant="outline" onClick={onRetry}>
        Tentar de novo
      </Button>
    </div>
  );
}
