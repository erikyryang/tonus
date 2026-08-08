import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogActions } from '@/components/ui/dialog';
import { apiMessage } from '@/lib/apiError';

/**
 * Confirmation for an action with consequences. The body doesn't ask "are you sure?":
 * it states what will happen — access revoked, exceptions deleted, WhatsApp
 * sent. That's the difference between an obstacle and information.
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
        <div className="text-sm text-foreground">{children}</div>

        {error != null && (
          <p role="alert" className="text-sm text-danger">
            {apiMessage(error)}
          </p>
        )}

        <DialogActions>
          {/* Back comes first: it's the one that receives focus when the dialog opens,
              and leaving the cursor over the destructive action would invite the Enter key. */}
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
