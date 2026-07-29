import { afterEach, describe, expect, it, vi } from 'vitest';
import { addDays, isWeekend, todayIsoDate } from './dates';

describe('dates', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('anda de um dia para o outro sem cair no mês errado', () => {
    expect(addDays('2026-07-29', 1)).toBe('2026-07-30');
    expect(addDays('2026-07-31', 1)).toBe('2026-08-01');
    expect(addDays('2026-08-01', -1)).toBe('2026-07-31');
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31');
  });

  /**
   * Data pura não pode passar por interpretação UTC: a oeste de Greenwich isso
   * transformaria a agenda de sábado na de sexta.
   */
  it('reconhece o fim de semana pela data, não pelo fuso do navegador', () => {
    expect(isWeekend('2026-08-01')).toBe(true);
    expect(isWeekend('2026-08-02')).toBe(true);
    expect(isWeekend('2026-07-29')).toBe(false);
  });

  it('usa o dia local como hoje', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 6, 29, 22, 30));

    expect(todayIsoDate()).toBe('2026-07-29');
  });
});
