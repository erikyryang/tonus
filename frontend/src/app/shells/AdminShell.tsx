import { NavLink, Outlet } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useSessionUser } from '@/features/auth/components/RequireRole';
import { useLogout } from '@/features/auth/session';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '../ThemeToggle';

const NAV_ITEMS = [
  { to: '/admin/schedule', label: 'Agenda' },
  { to: '/admin/students', label: 'Alunos' },
  { to: '/admin/teachers', label: 'Professores' },
  { to: '/admin/default-schedules', label: 'Horários' },
  { to: '/admin/announcements', label: 'Comunicados' },
  { to: '/admin/plans', label: 'Planos' },
  { to: '/admin/invoices', label: 'Faturas' },
  { to: '/admin/settings', label: 'Configuração' },
];

export function AdminShell() {
  const user = useSessionUser();
  const logout = useLogout();

  return (
    <div className="flex min-h-screen bg-background">
      <aside className="flex w-56 shrink-0 flex-col border-r border-border bg-card p-4">
        <p className="mb-6 text-lg font-semibold text-foreground">Tônus</p>
        <nav aria-label="Navegação principal" className="flex flex-col gap-1">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm text-foreground hover:bg-accent',
                  isActive && 'bg-accent font-medium text-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <ThemeToggle orientation="vertical" className="mt-auto" />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border bg-card px-6 py-3">
          <span className="text-sm text-foreground">{user.name}</span>
          <Button variant="outline" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
            {logout.isPending ? 'Saindo…' : 'Sair'}
          </Button>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
