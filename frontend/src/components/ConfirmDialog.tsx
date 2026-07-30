import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { apiMessage } from '@/lib/apiError';

/**
 * Confirmação de ação com consequência. O corpo não pergunta "tem certeza?":
 * conta o que vai acontecer — acesso revogado, exceções apagadas, WhatsApp
 * enviado. É a diferença entre um obstáculo e uma informação.
 */
export function ConfirmDialog({
  title,
  confirmLabel,
  pendingLabel,
  pending,
  error,
  onConfirm,
  onClose,
  children,
}: {
  title: string;
  confirmLabel: string;
  pendingLabel: string;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <Dialog title={title} onClose={onClose} busy={pending}>
      <div className="space-y-4">
        <div className="text-sm text-slate-700">{children}</div>

        {error != null && (
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          {/* Voltar vem primeiro: é ele que recebe o foco ao abrir o diálogo,
              e sair com o cursor sobre a ação destrutiva convidaria o Enter. */}
          <Button variant="outline" onClick={onClose} disabled={pending}>
            Voltar
          </Button>
          <Button onClick={onConfirm} disabled={pending}>
            {pending ? pendingLabel : confirmLabel}
          </Button>
        </DialogActions>
      </div>
    </Dialog>
  );
}
