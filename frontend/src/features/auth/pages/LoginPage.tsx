import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ApiError } from '@/lib/apiClient';
import { type SessionUser, authApi, sessionQueryKey } from '../api/authApi';
import { destinationFor } from '../session';

/** Single message: doesn't tell whoever is trying whether the login exists. */
const INVALID_CREDENTIALS = 'Login ou senha inválidos.';
const TOO_MANY_ATTEMPTS = 'Muitas tentativas seguidas. Aguarde um minuto e tente de novo.';
const UNREACHABLE = 'Não foi possível conectar. Verifique sua internet e tente de novo.';

const loginSchema = z.object({
  login: z.string().min(1, 'Informe o login.'),
  password: z.string().min(1, 'Informe a senha.'),
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { login: '', password: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await authApi.login(values);
      // The previous cache may belong to another user: the new session starts clean.
      queryClient.clear();
      const user = await queryClient.fetchQuery<SessionUser>({
        queryKey: sessionQueryKey,
        queryFn: authApi.me,
      });
      navigate(destinationFor(user.role), { replace: true });
    } catch (error) {
      setFormError(messageFor(error));
    }
  });

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4 rounded-lg bg-card p-6 shadow">
        <h1 className="text-xl font-semibold text-foreground">Tônus</h1>

        <div className="space-y-1">
          <Label htmlFor="login">Login</Label>
          <Input
            id="login"
            autoComplete="username"
            autoFocus
            aria-invalid={errors.login ? true : undefined}
            aria-describedby={errors.login ? 'login-error' : undefined}
            {...register('login')}
          />
          {errors.login && (
            <p id="login-error" className="text-sm text-danger">
              {errors.login.message}
            </p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="password">Senha</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
          {errors.password && (
            <p id="password-error" className="text-sm text-danger">
              {errors.password.message}
            </p>
          )}
        </div>

        {formError && (
          <p role="alert" className="text-sm text-danger">
            {formError}
          </p>
        )}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Entrando…' : 'Entrar'}
        </Button>
      </form>
    </main>
  );
}

/**
 * 401 has a fixed message — the backend's detail is also generic, but depending
 * on it would leave the "don't distinguish user from password" guarantee in the hands of whoever
 * edits the string on the other side. The other errors show what the backend said,
 * which then is useful information (rate limit, unavailability).
 */
function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return UNREACHABLE;
  if (error.status === 401) return INVALID_CREDENTIALS;
  if (error.status === 429) return TOO_MANY_ATTEMPTS;
  return error.detail ?? 'Não foi possível entrar. Tente de novo.';
}
