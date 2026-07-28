import { NavLink, Outlet } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useSessionUser } from '@/features/auth/components/RequireRole';
import { useLogout } from '@/features/auth/session';
import { cn } from '@/lib/utils';

const NAV_ITEMS = [
  { to: '/admin/schedule', label: 'Agenda' },
  { to: '/admin/students', label: 'Alunos' },
  { to: '/admin/teachers', label: 'Professores' },
  { to: '/admin/invoices', label: 'Faturas' },
  { to: '/admin/holidays', label: 'Feriados' },
];

export function AdminShell() {
  const user = useSessionUser();
  const logout = useLogout();

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="w-56 shrink-0 border-r border-slate-200 bg-white p-4">
        <p className="mb-6 text-lg font-semibold text-slate-900">Tônus</p>
        <nav aria-label="Navegação principal" className="flex flex-col gap-1">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm text-slate-700 hover:bg-slate-100',
                  isActive && 'bg-slate-100 font-medium text-slate-900',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <span className="text-sm text-slate-700">{user.name}</span>
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
