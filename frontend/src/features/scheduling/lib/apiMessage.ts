import { ApiError } from '@/lib/apiClient';

const FALLBACK = 'Não foi possível concluir a operação. Tente de novo.';

/**
 * A mensagem do backend, quando existe. O back é a autoridade sobre as regras
 * (data passada, feriado já cadastrado) e sabe qual delas barrou — trocá-la por
 * um texto genérico obrigaria o ADMIN a adivinhar o motivo.
 */
export function apiMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail ?? error.message;
  return FALLBACK;
}
