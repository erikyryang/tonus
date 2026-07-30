/**
 * Formatação pt-BR. Texto visível ao usuário passa por aqui — nada de
 * toLocaleString espalhado por componente, senão a mesma data aparece em dois
 * formatos em telas diferentes.
 */

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

const moneyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

/**
 * Data pura (yyyy-MM-dd) para dd/MM/yyyy.
 *
 * A conversão é textual de propósito: passar por Date faria o navegador
 * interpretar a string como meia-noite UTC e, a oeste de Greenwich, exibir o
 * dia anterior. Aula de dia 5 não pode virar dia 4 por causa de fuso.
 */
export function formatDate(isoDate: string): string {
  const match = ISO_DATE.exec(isoDate);
  if (!match) return isoDate;
  const [, year, month, day] = match;
  return `${day}/${month}/${year}`;
}

/** dd/MM — para rótulo curto, onde o ano já está implícito no contexto. */
export function formatDayMonth(isoDate: string): string {
  const match = ISO_DATE.exec(isoDate);
  if (!match) return isoDate;
  const [, , month, day] = match;
  return `${day}/${month}`;
}

/** Competência (yyyy-MM) para MM/yyyy — a fatura é do mês, não de um dia. */
export function formatReferenceMonth(referenceMonth: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(referenceMonth);
  if (!match) return referenceMonth;
  const [, year, month] = match;
  return `${month}/${year}`;
}

/** dd/MM/yyyy de volta para yyyy-MM-dd — o formato que a API espera. */
export function toIsoDate(brDate: string): string {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(brDate);
  if (!match) return brDate;
  const [, day, month, year] = match;
  return `${year}-${month}-${day}`;
}

/** Valor em reais para "R$ 1.234,50". */
export function formatMoney(value: number): string {
  return moneyFormatter.format(value);
}

/**
 * O que foi digitado no campo de dinheiro, em número.
 *
 * Aceita as duas formas que aparecem de verdade: "1.234,50" (pt-BR completo) e
 * "1234.50" (quem digita no teclado numérico). A vírgula é o desempate — se ela
 * está lá, o ponto só pode ser separador de milhar. NaN quando não dá para ler,
 * e é o zod de quem chama que decide o que fazer com isso.
 */
export function parseMoney(value: string): number {
  const trimmed = value.trim();
  const normalized = trimmed.includes(',') ? trimmed.replace(/\./g, '').replace(',', '.') : trimmed;
  return normalized.length === 0 ? Number.NaN : Number(normalized);
}
