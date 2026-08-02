import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isConflict } from '@/lib/apiError';
import {
  type BillingSettings,
  type InvoiceQuery,
  type PlanInput,
  type PlanQuery,
  billingApi,
  billingSettingsKey,
  enrollmentsKey,
  invoicesKey,
  myInvoicesKey,
  overdueStudentIdsKey,
  plansKey,
  plansLookupKey,
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
 * E vai junto o conjunto de quem está atrasado, que é outra chave: dar baixa na
 * última fatura vencida de um aluno tira o "Atrasado" dele na tela de alunos, e
 * essa tela não estava montada na hora da baixa — sem invalidar, ela voltaria
 * do cache afirmando um atraso que acabou de ser pago.
 *
 * O 409 também invalida. Ele significa que outro admin já deu a baixa — a lista
 * na tela está desatualizada exatamente como estaria depois de um sucesso, e
 * deixá-la como está mostraria ao ADMIN uma fatura aberta que não existe mais.
 */
export function useRegisterPayment() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: invoicesKey() }),
      queryClient.invalidateQueries({ queryKey: overdueStudentIdsKey }),
    ]);

  return useMutation({
    mutationFn: ({ id, paymentDate }: RegisterPaymentInput) => billingApi.registerPayment(id, paymentDate),
    onSuccess: invalidate,
    onError: (error) => {
      if (isConflict(error)) return invalidate();
    },
  });
}

export function usePlans(query: PlanQuery) {
  return useQuery({
    queryKey: plansKey(query),
    queryFn: () => billingApi.plans(query),
    placeholderData: keepPreviousData,
  });
}

/**
 * O catálogo inteiro, para o diálogo de planos do aluno. Só os ativos entram na
 * escolha: matricular alguém num plano desativado não geraria cobrança.
 */
export function usePlansLookup() {
  return useQuery({
    queryKey: plansLookupKey,
    queryFn: () => billingApi.plans({ active: true, page: 0 }),
    select: (page) => page.content,
  });
}

function usePlanInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: plansKey() });
}

export function useCreatePlan() {
  const invalidate = usePlanInvalidation();
  return useMutation({
    mutationFn: (input: PlanInput) => billingApi.createPlan(input),
    onSuccess: invalidate,
  });
}

export function useUpdatePlan() {
  const invalidate = usePlanInvalidation();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: PlanInput }) => billingApi.updatePlan(id, input),
    onSuccess: invalidate,
  });
}

export function useSetPlanActive() {
  const invalidate = usePlanInvalidation();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      active ? billingApi.activatePlan(id) : billingApi.deactivatePlan(id),
    onSuccess: invalidate,
  });
}

export function useEnrollmentsOfStudent(studentId: number | undefined) {
  return useQuery({
    queryKey: enrollmentsKey(studentId),
    queryFn: () => billingApi.enrollmentsOfStudent(studentId!),
    enabled: studentId !== undefined,
  });
}

/**
 * Matricular e desmatricular invalidam o prefixo das matrículas e o dos planos:
 * a contagem de alunos por plano — que a tela de planos usa para avisar antes
 * de desativar — muda junto.
 */
function useEnrollmentInvalidation() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: enrollmentsKey() }),
      queryClient.invalidateQueries({ queryKey: plansKey() }),
    ]);
}

export function useEnroll() {
  const invalidate = useEnrollmentInvalidation();
  return useMutation({
    mutationFn: ({ studentId, planId }: { studentId: number; planId: number }) =>
      billingApi.enroll(studentId, planId),
    onSuccess: invalidate,
  });
}

export function useSetEnrollmentActive() {
  const invalidate = useEnrollmentInvalidation();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      active ? billingApi.activateEnrollment(id) : billingApi.deactivateEnrollment(id),
    onSuccess: invalidate,
  });
}

export function useBillingSettings() {
  return useQuery({
    queryKey: billingSettingsKey,
    queryFn: () => billingApi.settings(),
  });
}

export function useUpdateBillingSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: BillingSettings) => billingApi.updateSettings(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: billingSettingsKey }),
  });
}

/**
 * Os alunos com fatura atrasada, em conjunto para a tela cruzar linha a linha.
 *
 * `undefined` enquanto não responde é a informação que importa: quem consome
 * precisa distinguir "ainda não sei" de "não está atrasado", senão a tela
 * afirma "Em dia" antes de ter perguntado.
 */
export function useOverdueStudentIds() {
  return useQuery({
    queryKey: overdueStudentIdsKey,
    queryFn: () => billingApi.overdueStudentIds(),
    select: (ids: number[]) => new Set(ids),
  });
}

export function useMyInvoices(page: number) {
  return useQuery({
    queryKey: myInvoicesKey(page),
    queryFn: () => billingApi.myInvoices(page),
    placeholderData: keepPreviousData,
  });
}
