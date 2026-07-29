import { ApiError } from '@/lib/apiClient';

const FALLBACK = 'Não foi possível concluir a operação. Tente de novo.';

/**
 * A mensagem do backend, quando existe. O back é a autoridade sobre as regras
 * (data passada, feriado já cadastrado, fatura já paga) e sabe qual delas
 * barrou — trocá-la por um texto genérico obrigaria o ADMIN a adivinhar o
 * motivo.
 *
 * Mora em lib/ e não numa feature: nasceu em scheduling, mas ler Problem
 * Details é do apiClient, não de um domínio.
 */
export function apiMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail ?? error.message;
  return FALLBACK;
}

/**
 * 409 é estado legítimo, não falha: alguém chegou primeiro. Quem trata assim
 * refaz a lista em vez de mostrar erro genérico — e sabe que repetir a mesma
 * ação não vai adiantar, porque o conflito não é transitório.
 */
export function isConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409;
}
