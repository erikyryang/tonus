import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  type WhatsAppSettingsInput,
  notificationApi,
  whatsappInstancesKey,
  whatsappStatusKey,
} from './notificationApi';

/** O pareamento termina no celular: com o QR na tela o status se refaz sozinho. */
const POLL_INTERVAL_MS = 5_000;

/**
 * `polling` vem da tela porque só ela sabe se o QR está visível. Fora disso o
 * intervalo é `false` — nada de aba esquecida repetindo chamada para sempre.
 */
export function useWhatsAppStatus(polling: boolean) {
  return useQuery({
    queryKey: whatsappStatusKey,
    queryFn: () => notificationApi.status(),
    refetchInterval: polling ? POLL_INTERVAL_MS : false,
  });
}

/**
 * As instâncias do gateway só fazem sentido quando o gateway respondeu: com
 * 503 (não configurado) não há o que listar, e perguntar de novo só produz um
 * segundo erro para a tela ignorar.
 */
export function useWhatsAppInstances(enabled: boolean) {
  return useQuery({
    queryKey: whatsappInstancesKey,
    queryFn: () => notificationApi.instances(),
    enabled,
  });
}

/** Toda mutação invalida o status explicitamente — ele é a fonte da tela. */
function useStatusInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: whatsappStatusKey });
}

export function useCreateWhatsAppSession() {
  const invalidate = useStatusInvalidation();
  return useMutation({
    mutationFn: () => notificationApi.createSession(),
    onSuccess: invalidate,
  });
}

export function useDeleteWhatsAppSession() {
  const invalidate = useStatusInvalidation();
  return useMutation({
    mutationFn: () => notificationApi.deleteSession(),
    onSuccess: invalidate,
  });
}

export function useUpdateWhatsAppSettings() {
  const invalidate = useStatusInvalidation();
  return useMutation({
    mutationFn: (input: WhatsAppSettingsInput) => notificationApi.updateSettings(input),
    onSuccess: invalidate,
  });
}
