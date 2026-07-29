import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { apiMessage } from '../lib/apiMessage';

/** Desfazer devolve o dia ao template — é a correção de um registro errado. */
export function UndoExceptionDialog({
  studentName,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  studentName: string;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog title="Desfazer exceção" onClose={onClose} busy={pending}>
      <p className="text-sm text-slate-700">
        Desfazer a exceção de <strong>{studentName}</strong> e devolver o dia ao horário padrão?
      </p>
      <p className="text-sm text-slate-500">O aluno não recebe aviso de que a exceção foi desfeita.</p>

      {error != null && (
        <p role="alert" className="text-sm text-red-600">
          {apiMessage(error)}
        </p>
      )}

      <DialogActions>
        <Button variant="outline" onClick={onClose} disabled={pending}>
          Voltar
        </Button>
        <Button onClick={onConfirm} disabled={pending}>
          {pending ? 'Desfazendo…' : 'Desfazer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
