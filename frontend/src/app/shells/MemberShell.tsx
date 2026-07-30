import { NavLink, Outlet } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useSessionUser } from '@/features/auth/components/RequireRole';
import { useLogout } from '@/features/auth/session';
import { cn } from '@/lib/utils';

/**
 * O professor tem uma tela só (a grade) e não ganha navegação: uma barra com
 * um item só ocuparia altura de celular para não oferecer escolha nenhuma.
 */
const STUDENT_NAV = [
  { to: '/me/schedule', label: 'Minha agenda' },
  { to: '/me/invoices', label: 'Minhas faturas' },
];

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

      {user.role === 'STUDENT' && (
        <nav aria-label="Navegação principal" className="flex gap-1 border-b border-slate-200 bg-white px-2 py-2">
          {STUDENT_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm text-slate-700',
                  isActive && 'bg-slate-100 font-medium text-slate-900',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      )}
      <main className="flex-1 p-4">
        <Outlet />
      </main>
    </div>
  );
}
