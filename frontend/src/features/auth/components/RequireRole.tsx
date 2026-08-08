import { Navigate, Outlet, useOutletContext } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import type { Role, SessionUser } from '../api/authApi';
import { destinationFor, useSessionState } from '../session';

export interface SessionContext {
  user: SessionUser;
}

/** In the screens below the guard the user is already resolved — no extra query. */
export function useSessionUser(): SessionUser {
  return useOutletContext<SessionContext>().user;
}

export function RequireRole({ allow }: { allow: Role[] }) {
  const session = useSessionState();

  if (session.status === 'loading') return <LoadingScreen />;
  if (session.status === 'unauthenticated') return <Navigate to="/login" replace />;
  if (session.status === 'error') return <SessionErrorScreen onRetry={session.retry} />;
  // A role without access goes to its own destination, not to an error page.
  if (!allow.includes(session.user.role)) return <Navigate to={destinationFor(session.user.role)} replace />;

  return <Outlet context={{ user: session.user } satisfies SessionContext} />;
}

export function LoadingScreen() {
  return (
    <div role="status" aria-live="polite" className="flex min-h-screen items-center justify-center text-muted-foreground">
      Carregando…
    </div>
  );
}

/**
 * Failure querying the session that isn't a 401. Doesn't redirect to login on
 * purpose: the session may still be alive and the problem may just be the API being down.
 */
export function SessionErrorScreen({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex min-h-screen flex-col items-center justify-center gap-4 p-4 text-center text-foreground"
    >
      <p>Não foi possível carregar sua sessão.</p>
      <Button variant="outline" onClick={onRetry}>
        Tentar de novo
      </Button>
    </div>
  );
}
