import { http, HttpResponse } from 'msw';
import type {
  WhatsAppInstance,
  WhatsAppSession,
  WhatsAppSettingsInput,
  WhatsAppState,
  WhatsAppStatus,
} from '@/features/notification/api/notificationApi';
import { problem } from './problem';

/** A versão que o container do gateway está rodando hoje — a "em vigor". */
export const IN_EFFECT_VERSION = '2.3000.1023204200';

/** QR base64 qualquer: o front não interpreta o conteúdo, só o entrega ao <img>. */
export const QR_BASE64 = 'iVBORw0KGgoAAAANSUhEUg==';

export function whatsappStatus(overrides: Partial<WhatsAppStatus> = {}): WhatsAppStatus {
  return {
    instance: 'tonus',
    state: 'DISCONNECTED',
    maskedPhone: null,
    phoneVersion: { inEffect: IN_EFFECT_VERSION, desired: IN_EFFECT_VERSION, pending: false },
    ...overrides,
  };
}

/**
 * A resposta vem de uma função para o teste poder trocá-la depois de uma
 * mutação — é assim que se observa "invalidou e refez a chamada" sem espiar o
 * TanStack Query por dentro, mesmo padrão de `invoicesHandler`.
 *
 * `log` conta as chamadas: o critério de aceite do polling é sobre o que sai na
 * rede, e "parou de repetir" só se vê contando.
 */
export function statusHandler(resolve: () => WhatsAppStatus, log?: number[]) {
  return http.get('/api/whatsapp/status', () => {
    log?.push(Date.now());
    return HttpResponse.json(resolve());
  });
}

export function statusOk(status: WhatsAppStatus = whatsappStatus()) {
  return statusHandler(() => status);
}

/** Problem Details do back: 503 = gateway não configurado, 502 = gateway fora do ar. */
export function statusProblem(status: number, title: string, detail: string) {
  return http.get('/api/whatsapp/status', () => problem(status, title, detail));
}

export function instance(name: string, state: WhatsAppState = 'DISCONNECTED'): WhatsAppInstance {
  return { name, state };
}

export function instancesHandler(resolve: () => WhatsAppInstance[]) {
  return http.get('/api/whatsapp/instances', () => HttpResponse.json({ instances: resolve() }));
}

export function instancesList(...instances: WhatsAppInstance[]) {
  return instancesHandler(() => instances);
}

export function instancesProblem(status: number, title: string, detail: string) {
  return http.get('/api/whatsapp/instances', () => problem(status, title, detail));
}

export function createSessionHandler(resolve: () => WhatsAppSession, onRequest?: () => void) {
  return http.post('/api/whatsapp/session', () => {
    onRequest?.();
    return HttpResponse.json(resolve());
  });
}

export function createSessionOk(qrCode: string | null = QR_BASE64, overrides: Partial<WhatsAppStatus> = {}) {
  return createSessionHandler(() => ({ ...whatsappStatus({ state: 'CONNECTING', ...overrides }), qrCode }));
}

export function deleteSessionOk(onRequest?: () => void) {
  return http.delete('/api/whatsapp/session', () => {
    onRequest?.();
    return HttpResponse.json(whatsappStatus({ state: 'DISCONNECTED' }));
  });
}

export function updateSettingsOk(onRequest?: (body: WhatsAppSettingsInput) => WhatsAppStatus | void) {
  return http.put('/api/whatsapp/settings', async ({ request }) => {
    const body = (await request.json()) as WhatsAppSettingsInput;
    const status = onRequest?.(body);
    return HttpResponse.json(
      status ??
        whatsappStatus({
          instance: body.instance,
          phoneVersion: {
            inEffect: IN_EFFECT_VERSION,
            desired: body.desiredPhoneVersion,
            pending: body.desiredPhoneVersion !== IN_EFFECT_VERSION,
          },
        }),
    );
  });
}
