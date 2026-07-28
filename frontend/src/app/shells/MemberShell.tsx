import { Outlet } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useSessionUser } from '@/features/auth/components/RequireRole';
import { useLogout } from '@/features/auth/session';

/** Shell mobile-first de aluno e professor. */
export function MemberShell() {
  const user = useSessionUser();
  const logout = useLogout();

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
        <span className="text-sm font-medium text-slate-700">{user.name}</span>
        <Button variant="ghost" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
          {logout.isPending ? 'Saindo…' : 'Sair'}
        </Button>
      </header>
      <main className="flex-1 p-4">
        <Outlet />
      </main>
    </div>
  );
}
