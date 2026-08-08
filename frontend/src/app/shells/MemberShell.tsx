import { NavLink, Outlet } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useSessionUser } from '@/features/auth/components/RequireRole';
import { useLogout } from '@/features/auth/session';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '../ThemeToggle';

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
    <div className="flex min-h-screen flex-col bg-background">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-card px-4 py-3">
        <span className="text-sm font-medium text-foreground">{user.name}</span>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Button variant="ghost" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
            {logout.isPending ? 'Saindo…' : 'Sair'}
          </Button>
        </div>
      </header>

      {user.role === 'STUDENT' && (
        <nav aria-label="Navegação principal" className="flex gap-1 border-b border-border bg-card px-2 py-2">
          {STUDENT_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm text-foreground',
                  isActive && 'bg-accent font-medium text-foreground',
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
