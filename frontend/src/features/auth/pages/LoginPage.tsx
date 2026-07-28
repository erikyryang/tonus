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

/** Mensagem única: não conta a quem tenta se o login existe. */
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
      // O cache anterior pode ser de outro usuário: a sessão nova começa limpa.
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
    <main className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <form onSubmit={onSubmit} noValidate className="w-full max-w-sm space-y-4 rounded-lg bg-white p-6 shadow">
        <h1 className="text-xl font-semibold text-slate-900">Tônus</h1>

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
            <p id="login-error" className="text-sm text-red-600">
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
            <p id="password-error" className="text-sm text-red-600">
              {errors.password.message}
            </p>
          )}
        </div>

        {formError && (
          <p role="alert" className="text-sm text-red-600">
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
 * 401 tem mensagem fixa — o detail do backend também é genérico, mas depender
 * dele deixaria a garantia de "não distinguir usuário de senha" na mão de quem
 * editar a string do outro lado. Os demais erros mostram o que o backend disse,
 * que aí sim é informação útil (rate limit, indisponibilidade).
 */
function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return UNREACHABLE;
  if (error.status === 401) return INVALID_CREDENTIALS;
  if (error.status === 429) return TOO_MANY_ATTEMPTS;
  return error.detail ?? 'Não foi possível entrar. Tente de novo.';
}
