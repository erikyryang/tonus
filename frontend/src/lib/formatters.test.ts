import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, toIsoDate } from '@/lib/formatters';

describe('formatDate', () => {
  it('converte data ISO para dd/MM/yyyy', () => {
    expect(formatDate('2026-07-28')).toBe('28/07/2026');
  });

  /** A regressão clássica: passar por Date e perder um dia no fuso de Brasília. */
  it('não desloca o dia por fuso horário', () => {
    expect(formatDate('2026-01-01')).toBe('01/01/2026');
    expect(formatDate('2026-12-31')).toBe('31/12/2026');
  });

  it('devolve a entrada intacta quando não é data ISO', () => {
    expect(formatDate('sem data')).toBe('sem data');
  });
});

describe('toIsoDate', () => {
  it('faz o caminho de volta para o formato da API', () => {
    expect(toIsoDate('28/07/2026')).toBe('2026-07-28');
  });
});

describe('formatMoney', () => {
  it('formata em real com separador pt-BR', () => {
    // O Intl usa espaço não-quebrável entre símbolo e número.
    expect(formatMoney(1234.5).replace(/\u00a0/g, ' ')).toBe('R$ 1.234,50');
    expect(formatMoney(0).replace(/\u00a0/g, ' ')).toBe('R$ 0,00');
  });

  it('arredonda para dois decimais', () => {
    expect(formatMoney(10.999).replace(/\u00a0/g, ' ')).toBe('R$ 11,00');
  });
});
