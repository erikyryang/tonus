import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { LoadingScreen, RequireRole, SessionErrorScreen } from '@/features/auth/components/RequireRole';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { destinationFor, useSessionState } from '@/features/auth/session';
import { InvoicesPage } from '@/features/billing/pages/InvoicesPage';
import { MyInvoicesPage } from '@/features/billing/pages/MyInvoicesPage';
import { SubscriptionsPage } from '@/features/billing/pages/SubscriptionsPage';
import { StudentsPage } from '@/features/registration/pages/StudentsPage';
import { TeachersPage } from '@/features/registration/pages/TeachersPage';
import { DefaultSchedulesPage } from '@/features/scheduling/pages/DefaultSchedulesPage';
import { AnnouncementsPage } from '@/features/scheduling/pages/AnnouncementsPage';
import { MyGridPage } from '@/features/scheduling/pages/MyGridPage';
import { MySchedulePage } from '@/features/scheduling/pages/MySchedulePage';
import { SchedulePage } from '@/features/scheduling/pages/SchedulePage';
import { setUnauthorizedHandler } from '@/lib/apiClient';
import { AdminShell } from './shells/AdminShell';
import { MemberShell } from './shells/MemberShell';

export function AppRoutes() {
  return (
    <>
      <UnauthorizedBridge />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RootRedirect />} />

        <Route element={<RequireRole allow={['ADMIN']} />}>
          <Route path="/admin" element={<AdminShell />}>
            <Route index element={<Navigate to="/admin/schedule" replace />} />
            <Route path="schedule" element={<SchedulePage />} />
            <Route path="students" element={<StudentsPage />} />
            <Route path="teachers" element={<TeachersPage />} />
            <Route path="default-schedules" element={<DefaultSchedulesPage />} />
            <Route path="announcements" element={<AnnouncementsPage />} />
            <Route path="subscriptions" element={<SubscriptionsPage />} />
            <Route path="invoices" element={<InvoicesPage />} />
          </Route>
        </Route>

        <Route element={<RequireRole allow={['STUDENT']} />}>
          <Route element={<MemberShell />}>
            <Route path="/me/schedule" element={<MySchedulePage />} />
            <Route path="/me/invoices" element={<MyInvoicesPage />} />
          </Route>
        </Route>

        <Route element={<RequireRole allow={['TEACHER']} />}>
          <Route element={<MemberShell />}>
            <Route path="/me/grid" element={<MyGridPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}

/** Raiz não tem tela própria: resolve o destino pelo papel da sessão. */
function RootRedirect() {
  const session = useSessionState();

  if (session.status === 'loading') return <LoadingScreen />;
  if (session.status === 'unauthenticated') return <Navigate to="/login" replace />;
  if (session.status === 'error') return <SessionErrorScreen onRetry={session.retry} />;
  return <Navigate to={destinationFor(session.user.role)} replace />;
}

/** Liga o 401 sem recuperação do apiClient ao roteador: cache limpo e /login. */
function UnauthorizedBridge() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    setUnauthorizedHandler(() => {
      queryClient.clear();
      navigate('/login', { replace: true });
    });
    return () => setUnauthorizedHandler(() => {});
  }, [navigate, queryClient]);

  return null;
}
