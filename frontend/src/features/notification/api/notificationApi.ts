import { apiClient } from '@/lib/apiClient';

/**
 * Espelha os DTOs da SPEC-013 à mão, como o resto do front — contrato muda,
 * mudam os dois lados no mesmo PR.
 *
 * `INSTANCE_MISSING` é estado normal, não erro: é o caso da instância
 * configurada que ainda não existe no gateway.
 */
export type WhatsAppState = 'INSTANCE_MISSING' | 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED';

/**
 * `inEffect` é o melhor esforço possível (a versão que a aplicação leu do
 * ambiente); `desired` é a intenção gravada. Enquanto diferirem, `pending`
 * marca que o gateway ainda roda a antiga — aplicar é recriar o container.
 */
export interface PhoneVersion {
  inEffect: string;
  desired: string;
  pending: boolean;
}

export interface WhatsAppStatus {
  instance: string;
  state: WhatsAppState;
  /** Já mascarado pelo back. O número completo não sai da API. */
  maskedPhone: string | null;
  phoneVersion: PhoneVersion;
}

/** O QR só existe na resposta do POST, e só enquanto vale. Nunca é persistido. */
export interface WhatsAppSession extends WhatsAppStatus {
  qrCode: string | null;
}

export interface WhatsAppInstance {
  name: string;
  state: WhatsAppState;
}

export interface WhatsAppSettingsInput {
  instance: string;
  desiredPhoneVersion: string;
}

export const whatsappStatusKey = ['notification', 'whatsapp', 'status'] as const;
export const whatsappInstancesKey = ['notification', 'whatsapp', 'instances'] as const;

export const notificationApi = {
  status: () => apiClient.get<WhatsAppStatus>('/whatsapp/status'),
  instances: () =>
    apiClient
      .get<{ instances: WhatsAppInstance[] }>('/whatsapp/instances')
      .then((response) => response.instances),
  updateSettings: (input: WhatsAppSettingsInput) =>
    apiClient.put<WhatsAppStatus>('/whatsapp/settings', input),
  createSession: () => apiClient.post<WhatsAppSession>('/whatsapp/session'),
  deleteSession: () => apiClient.delete<WhatsAppStatus>('/whatsapp/session'),
};
