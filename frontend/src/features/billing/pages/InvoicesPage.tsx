import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { apiMessage } from '@/lib/apiError';
import { formatDate, formatMoney, formatReferenceMonth } from '@/lib/formatters';
import { cn } from '@/lib/utils';
import type { Invoice, InvoiceStatus } from '../api/billingApi';
import { useInvoices, useRegisterPayment } from '../api/billingQueries';
import { InvoiceStatusBadge } from '../components/InvoiceStatusBadge';
import { RegisterPaymentDialog } from '../components/RegisterPaymentDialog';
import { studentLabel } from '../lib/studentLabel';

/**
 * "Todas" é o default; "Atrasadas" é o atalho que a spec pede em destaque.
 * `empty` acompanha o filtro porque "Nenhuma fatura" sob "Atrasadas" leria como
 * "não existe fatura nenhuma" — o oposto do que o ADMIN precisa concluir.
 */
const FILTERS: Array<{ label: string; status?: InvoiceStatus; empty: string }> = [
  { label: 'Todas', empty: 'Nenhuma fatura registrada ainda.' },
  { label: 'Abertas', status: 'OPEN', empty: 'Nenhuma fatura aberta.' },
  { label: 'Atrasadas', status: 'OVERDUE', empty: 'Nenhuma fatura atrasada.' },
  { label: 'Pagas', status: 'PAID', empty: 'Nenhuma fatura paga.' },
];

const STATUSES: InvoiceStatus[] = ['OPEN', 'PAID', 'OVERDUE'];

/**
 * Filtro e página moram na URL, e não em useState — mesma decisão da agenda:
 * recarregar, voltar no histórico e mandar o link de "as atrasadas" para alguém
 * continuam funcionando.
 */
export function InvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = parsePage(searchParams.get('page'));

  const [pending, setPending] = useState<Invoice | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const invoices = useInvoices({ status, page });
  const registerPayment = useRegisterPayment();

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  function closeDialog() {
    setPending(null);
    registerPayment.reset();
  }

  const data = invoices.data;

  /**
   * A última página some debaixo do ADMIN quando ele dá baixa no único item
   * dela sob "Atrasadas": a fatura sai do filtro e a página deixa de existir.
   * Sem recuar, a tela diria "nenhuma fatura atrasada" com a página 1 cheia.
   * Cobre também `?page=` fora do intervalo digitado na URL.
   *
   * O alvo é um número (ou null), e não uma função, para o efeito depender só
   * de primitivos — `setSearchParams` é estável e nada mais entra na lista.
   */
  const clampTo =
    data != null && data.number > 0 && data.content.length === 0 && data.totalPages > 0
      ? data.totalPages - 1
      : null;

  useEffect(() => {
    // replace: a página inexistente não merece entrada no histórico — voltar
    // nela só provocaria o mesmo recuo de novo.
    if (clampTo != null) setSearchParams(queryFor(status, clampTo), { replace: true });
  }, [clampTo, status, setSearchParams]);

  return (
    <section className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Faturas</h1>

      <div role="group" aria-label="Filtrar por status" className="flex gap-2">
        {FILTERS.map((filter) => (
          <Button
            key={filter.label}
            size="sm"
            variant={filter.status === status ? 'default' : 'outline'}
            aria-pressed={filter.status === status}
            // Trocar de filtro volta para a primeira página: a página 3 das
            // atrasadas raramente existe quando o filtro era "todas".
            onClick={() => setSearchParams(queryFor(filter.status, 0))}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <div role="status" aria-live="polite">
        {toast && (
          <span className="inline-block rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{toast}</span>
        )}
      </div>

      {invoices.isPending && <p className="text-sm text-slate-500">Carregando as faturas…</p>}

      {!invoices.isPending && invoices.isFetching && <p className="text-sm text-slate-400">Atualizando…</p>}

      {invoices.isError && (
        <div className="space-y-2">
          <p role="alert" className="text-sm text-red-600">
            {apiMessage(invoices.error)}
          </p>
          <Button variant="outline" size="sm" onClick={() => void invoices.refetch()}>
            Tentar de novo
          </Button>
        </div>
      )}

      {/* Enquanto o recuo não acontece, o vazio da página inexistente mentiria. */}
      {data && data.content.length === 0 && clampTo == null && (
        <p className="text-sm text-slate-500">{emptyMessageFor(status)}</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <InvoiceTable invoices={data.content} onRegisterPayment={setPending} />
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            onChange={(next) => setSearchParams(queryFor(status, next))}
          />
        </>
      )}

      {pending && (
        <RegisterPaymentDialog
          invoice={pending}
          pending={registerPayment.isPending}
          error={registerPayment.error}
          onClose={closeDialog}
          onConfirm={({ paymentDate }) =>
            registerPayment.mutate(
              { id: pending.id, paymentDate },
              {
                onSuccess: () => {
                  // "será avisado", não "recebeu": a entrega do WhatsApp é
                  // assíncrona (evento + reentrega). No instante da resposta
                  // HTTP nada foi entregue ainda, e o toast não pode mentir.
                  setToast('Baixa registrada. O aluno será avisado por WhatsApp.');
                  closeDialog();
                },
              },
            )
          }
        />
      )}
    </section>
  );
}

function InvoiceTable({
  invoices,
  onRegisterPayment,
}: {
  invoices: Invoice[];
  onRegisterPayment: (invoice: Invoice) => void;
}) {
  return (
    <table className="w-full border-collapse text-sm">
      <caption className="sr-only">Faturas</caption>
      <thead>
        <tr className="border-b border-slate-200 text-left text-slate-500">
          <th scope="col" className="px-3 py-2 font-medium">
            Aluno
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Competência
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Valor
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Vencimento
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            Status
          </th>
          <th scope="col" className="px-3 py-2">
            <span className="sr-only">Ações</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {invoices.map((invoice) => (
          <tr
            key={invoice.id}
            // O gancho do destaque da linha atrasada, e o que o teste observa:
            // a cor sozinha não é asserção nem sinal acessível.
            data-status={invoice.status}
            className={cn(
              'border-b border-slate-100',
              invoice.status === 'OVERDUE' && 'bg-red-50',
            )}
          >
            <td className="px-3 py-2 text-slate-900">{studentLabel(invoice)}</td>
            <td className="px-3 py-2">{formatReferenceMonth(invoice.referenceMonth)}</td>
            <td className="px-3 py-2">{formatMoney(invoice.amount)}</td>
            <td className="px-3 py-2">{formatDate(invoice.dueDate)}</td>
            <td className="px-3 py-2">
              <InvoiceStatusBadge status={invoice.status} />
              {invoice.paymentDate && (
                <span className="ml-2 text-xs text-slate-500">pago em {formatDate(invoice.paymentDate)}</span>
              )}
            </td>
            <td className="px-3 py-2 text-right">
              {/* Pagamento é imutável no back: PAGA não tem ação a oferecer. */}
              {invoice.status !== 'PAID' && (
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Dar baixa na fatura de ${studentLabel(invoice)}`}
                  onClick={() => onRegisterPayment(invoice)}
                >
                  Dar baixa
                </Button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center gap-3 text-sm text-slate-600">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Página anterior
      </Button>
      <span>
        Página {page + 1} de {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Próxima página
      </Button>
    </div>
  );
}

/**
 * A query string canônica da tela. Só o que foge do default aparece: sem
 * filtro e na primeira página a URL fica limpa, e /admin/invoices continua
 * sendo o link que se manda para alguém.
 */
function queryFor(status: InvoiceStatus | undefined, page: number): URLSearchParams {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (page > 0) params.set('page', String(page));
  return params;
}

function emptyMessageFor(status: InvoiceStatus | undefined): string {
  return FILTERS.find((filter) => filter.status === status)?.empty ?? FILTERS[0]!.empty;
}

function parseStatus(value: string | null): InvoiceStatus | undefined {
  return STATUSES.find((status) => status === value);
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 0;
}
