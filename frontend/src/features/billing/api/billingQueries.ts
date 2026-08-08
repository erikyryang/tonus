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
    /** Switching filter or page doesn't flash the table to blank. */
    placeholderData: keepPreviousData,
  });
}

export interface RegisterPaymentInput {
  id: number;
  paymentDate: string;
}

/**
 * The invalidation is on the ['billing','invoices'] prefix, not the current page: settling
 * takes the invoice out of the "Overdue" filter and puts it into "Paid", so any
 * filter and page combination in cache has started lying.
 *
 * And the set of who's overdue goes along, which is another key: settling the
 * last overdue invoice of a student removes their "Overdue" status on the students screen, and
 * that screen wasn't mounted at the time of settling — without invalidating, it would come back
 * from cache stating a delay that was just paid.
 *
 * The 409 also invalidates. It means another admin already settled it — the list
 * on the screen is out of date exactly as it would be after a success, and
 * leaving it as is would show the ADMIN an open invoice that no longer exists.
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
 * The whole catalog, for the student's plans dialog. Only the active ones enter the
 * choice: enrolling someone in a deactivated plan wouldn't generate a charge.
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
 * Enrolling and unenrolling invalidate the enrollments prefix and the plans one:
 * the count of students per plan — which the plans screen uses to warn before
 * deactivating — changes along with it.
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
 * The students with an overdue invoice, as a set for the screen to cross-reference row by row.
 *
 * `undefined` while it hasn't responded is the information that matters: the consumer
 * needs to distinguish "don't know yet" from "not overdue", or else the screen
 * states "Up to date" before having asked.
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
