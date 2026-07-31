import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, parseMoney, toIsoDate } from '@/lib/formatters';

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

describe('parseMoney', () => {
  it('l\u00ea o pt-BR completo', () => {
    expect(parseMoney('1.234,50')).toBe(1234.5);
    expect(parseMoney('350,50')).toBe(350.5);
  });

  it('l\u00ea o ponto decimal de quem digita no teclado num\u00e9rico', () => {
    expect(parseMoney('1234.50')).toBe(1234.5);
    expect(parseMoney('0.07')).toBe(0.07);
  });

  /**
   * A regress\u00e3o que custa dinheiro: "1.500" \u00e9 mil e quinhentos em pt-BR, e lido
   * como 1,5 viraria uma assinatura de R$ 1,50 que passa em toda valida\u00e7\u00e3o \u2014
   * o m\u00ednimo \u00e9 um centavo, e ningu\u00e9m reclamaria.
   */
  it('trata o ponto como milhar quando os grupos s\u00e3o de tr\u00eas d\u00edgitos', () => {
    expect(parseMoney('1.500')).toBe(1500);
    expect(parseMoney('1.234.567')).toBe(1234567);
  });

  /**
   * Grupo de milhar n\u00e3o come\u00e7a em zero: "0.500" \u00e9 meio real. L\u00ea-lo como
   * quinhentos seria o erro inverso do que a regra de milhar veio consertar \u2014
   * e mil vezes mais caro, porque cobra a mais em vez de a menos.
   */
  it('n\u00e3o trata o zero \u00e0 esquerda como milhar', () => {
    expect(parseMoney('0.500')).toBe(0.5);
    expect(parseMoney('0.750')).toBe(0.75);
  });

  it('devolve NaN quando n\u00e3o d\u00e1 para ler', () => {
    expect(parseMoney('')).toBeNaN();
    expect(parseMoney('  ')).toBeNaN();
    expect(parseMoney('abc')).toBeNaN();
  });
});
