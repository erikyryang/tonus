import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { formatDate } from '@/lib/formatters';
import { apiMessage } from '@/lib/apiError';

/**
 * A confirmação diz o efeito real, não "tem certeza?": cancelar dispara
 * WhatsApp para o aluno. Quem confirma sem saber disso avisa o aluno por
 * engano, e não há como desavisar.
 */
export function CancelClassDialog({
  studentName,
  date,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  studentName: string;
  date: string;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog title="Cancelar aula" onClose={onClose} busy={pending}>
      <p className="text-sm text-slate-700">
        Cancelar a aula de <strong>{studentName}</strong> em {formatDate(date)}?
      </p>
      <p className="text-sm font-medium text-slate-900">O aluno será avisado por WhatsApp.</p>

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
          {pending ? 'Cancelando…' : 'Cancelar aula'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
