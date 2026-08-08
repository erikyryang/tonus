import type { InvoiceStatus } from '../api/billingApi';
import { cn } from '@/lib/utils';

export const STATUS_LABEL: Record<InvoiceStatus, string> = {
  OPEN: 'Aberta',
  PAID: 'Paga',
  OVERDUE: 'Atrasada',
};

/**
 * O rótulo carrega a distinção, não só a cor: ATRASADA é o que o ADMIN caça
 * nesta tela, e quem não distingue vermelho de âmbar precisa lê-la mesmo assim.
 */
const STATUS_STYLE: Record<InvoiceStatus, string> = {
  OPEN: 'bg-muted text-muted-foreground',
  PAID: 'bg-success-subtle text-success-emphasis',
  OVERDUE: 'bg-danger-subtle font-semibold text-danger-emphasis',
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return (
    <span className={cn('inline-block rounded-full px-2 py-0.5 text-xs', STATUS_STYLE[status])}>
      {STATUS_LABEL[status]}
    </span>
  );
}
