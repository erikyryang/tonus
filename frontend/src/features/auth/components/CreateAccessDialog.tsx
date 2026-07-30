import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { apiMessage } from '@/lib/apiError';

/**
 * O mínimo de senha é mais duro que o do back (que só exige não-vazio) de
 * propósito: aqui se cria a credencial de outra pessoa, e uma senha de três
 * letras digitada com pressa vira acesso frouxo que ninguém revisita.
 */
const schema = z.object({
  login: z.string().trim().min(3, 'O login precisa de ao menos 3 caracteres.'),
  password: z.string().min(8, 'A senha precisa de ao menos 8 caracteres.'),
});

export type AccessForm = z.infer<typeof schema>;

export function CreateAccessDialog({
  studentName,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  studentName: string;
  pending: boolean;
  error: unknown;
  onConfirm: (values: AccessForm) => void;
  onClose: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AccessForm>({
    resolver: zodResolver(schema),
    defaultValues: { login: '', password: '' },
  });

  return (
    <Dialog title="Criar acesso" onClose={onClose} busy={pending}>
      <form onSubmit={handleSubmit(onConfirm)} noValidate className="space-y-4">
        <p className="text-sm text-slate-700">
          Login do aluno <strong>{studentName}</strong>.
        </p>

        <div className="space-y-1">
          <Label htmlFor="login">Login</Label>
          <Input id="login" autoComplete="off" aria-invalid={errors.login ? true : undefined} {...register('login')} />
          {errors.login && <p className="text-sm text-red-600">{errors.login.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="password">Senha</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.password ? true : undefined}
            {...register('password')}
          />
          {errors.password && <p className="text-sm text-red-600">{errors.password.message}</p>}
        </div>

        {/* A senha é combinada fora do sistema: não existe "esqueci minha senha"
            nesta fase, e quem cria o acesso é quem a entrega ao aluno. */}
        <p className="text-sm text-slate-600">Anote a senha: ela não é exibida de novo.</p>

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Voltar
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? 'Criando…' : 'Criar acesso'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
