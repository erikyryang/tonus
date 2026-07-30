import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isConflict } from '@/lib/apiError';
import {
  type InvoiceQuery,
  type SubscriptionInput,
  type SubscriptionQuery,
  billingApi,
  invoicesKey,
  myInvoicesKey,
  subscriptionsKey,
} from './billingApi';

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

export function useSubscriptions(query: SubscriptionQuery) {
  return useQuery({
    queryKey: subscriptionsKey(query),
    queryFn: () => billingApi.subscriptions(query),
    placeholderData: keepPreviousData,
  });
}

/**
 * A invalidação é do prefixo: criar ou desativar assinatura muda a combinação
 * de filtro e página inteira, não só a linha mexida.
 */
function useSubscriptionInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: subscriptionsKey() });
}

/** 409 é "esse aluno já tem assinatura": a lista na tela já está desatualizada. */
export function useCreateSubscription() {
  const invalidate = useSubscriptionInvalidation();
  return useMutation({
    mutationFn: (input: SubscriptionInput) => billingApi.createSubscription(input),
    onSuccess: invalidate,
    onError: (error) => {
      if (isConflict(error)) invalidate();
    },
  });
}

export interface UpdateSubscriptionInput {
  id: number;
  input: SubscriptionInput;
}

export function useUpdateSubscription() {
  const invalidate = useSubscriptionInvalidation();
  return useMutation({
    mutationFn: ({ id, input }: UpdateSubscriptionInput) => billingApi.updateSubscription(id, input),
    onSuccess: invalidate,
  });
}

export function useSetSubscriptionActive() {
  const invalidate = useSubscriptionInvalidation();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      active ? billingApi.activateSubscription(id) : billingApi.deactivateSubscription(id),
    onSuccess: invalidate,
  });
}

export function useMyInvoices(page: number) {
  return useQuery({
    queryKey: myInvoicesKey(page),
    queryFn: () => billingApi.myInvoices(page),
    placeholderData: keepPreviousData,
  });
}
