import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isConflict } from '@/lib/apiError';
import { type InvoiceQuery, billingApi, invoicesKey } from './billingApi';

export function useInvoices(query: InvoiceQuery) {
  return useQuery({
    queryKey: invoicesKey(query),
    queryFn: () => billingApi.invoices(query),
    /** Trocar de filtro ou de página não pisca a tabela para branco. */
    placeholderData: keepPreviousData,
  });
}

export interface RegisterPaymentInput {
  id: number;
  paymentDate: string;
}

/**
 * A invalidação é do prefixo ['billing','invoices'], não da página atual: a
 * baixa tira a fatura do filtro "Atrasadas" e a põe em "Pagas", então qualquer
 * combinação de filtro e página em cache passou a mentir.
 *
 * O 409 também invalida. Ele significa que outro admin já deu a baixa — a lista
 * na tela está desatualizada exatamente como estaria depois de um sucesso, e
 * deixá-la como está mostraria ao ADMIN uma fatura aberta que não existe mais.
 */
export function useRegisterPayment() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: invoicesKey() });

  return useMutation({
    mutationFn: ({ id, paymentDate }: RegisterPaymentInput) => billingApi.registerPayment(id, paymentDate),
    onSuccess: invalidate,
    onError: (error) => {
      if (isConflict(error)) return invalidate();
    },
  });
}
