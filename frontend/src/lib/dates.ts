/**
 * Aritmética de data pura (yyyy-MM-dd).
 *
 * Nada aqui usa o construtor Date sobre a string: `new Date('2026-08-01')` é
 * lido como meia-noite UTC e, a oeste de Greenwich, volta como 31/07. A agenda
 * inteira é indexada por dia, então esse deslize trocaria a agenda de um dia
 * pela do outro. As contas acontecem em UTC, onde não existe horário de verão
 * nem dia de 23 horas.
 */

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function isIsoDate(value: string | null | undefined): value is string {
  return value != null && ISO_DATE.test(value);
}

/** Hoje no fuso do navegador — quem abre a recepção às 22h quer o dia dele. */
export function todayIsoDate(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function addDays(isoDate: string, days: number): string {
  const date = toUtcDate(isoDate);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function isWeekend(isoDate: string): boolean {
  const weekday = toUtcDate(isoDate).getUTCDay();
  return weekday === 0 || weekday === 6;
}

const weekdayFormatter = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', timeZone: 'UTC' });

/** "quarta-feira" — rótulo de apoio da navegação de data. */
export function formatWeekday(isoDate: string): string {
  return weekdayFormatter.format(toUtcDate(isoDate));
}

function toUtcDate(isoDate: string): Date {
  const match = ISO_DATE.exec(isoDate);
  if (!match) return new Date(Number.NaN);
  const [, year, month, day] = match;
  return new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}
