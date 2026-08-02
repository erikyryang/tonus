import { cn } from '@/lib/utils';

/**
 * A situação de cobrança de um aluno, derivada de quem tem fatura ATRASADA.
 *
 * "Atrasado" é o que o ADMIN caça; "Em dia" existe para o contrário ser uma
 * afirmação e não a ausência de marca — célula vazia se confunde com dado que
 * não chegou. Por isso o componente não é renderizado enquanto a consulta não
 * responde: quem decide isso é a tela, que sabe se já perguntou.
 *
 * O rótulo carrega a distinção, não só a cor — mesma razão do InvoiceStatusBadge.
 */
export function BillingStatusBadge({ overdue }: { overdue: boolean }) {
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs',
        overdue ? 'bg-red-100 font-semibold text-red-800' : 'bg-slate-100 text-slate-700',
      )}
    >
      {overdue ? 'Atrasado' : 'Em dia'}
    </span>
  );
}
