import { describe, expect, it } from 'vitest';
import {
  costMicros,
  isSyntheticModel,
  microsToUsdString,
  resolvePricing,
} from '../../src/domain/usageCost.js';

// Os numeros vem de `ModelPricingTableTest.kt`, e nao de uma conta refeita aqui:
// este modulo espelha `ModelPricing.kt`, e a prova de que o espelho esta certo e
// bater com os mesmos valores que o original ja afirma.
describe('aritmetica de preco no servidor', () => {
  it('deriva as cinco tarifas do sonnet 5', () => {
    const pricing = resolvePricing('claude-sonnet-5');

    expect(pricing).not.toBeNull();
    expect(pricing?.inputMicrosPerMillion).toBe(2_000_000n);
    expect(pricing?.outputMicrosPerMillion).toBe(10_000_000n);
    expect(pricing?.cacheReadMicrosPerMillion).toBe(200_000n);
    expect(pricing?.cacheWrite5mMicrosPerMillion).toBe(2_500_000n);
    expect(pricing?.cacheWrite1hMicrosPerMillion).toBe(4_000_000n);
  });

  // A tarifa do Sonnet 5 ja foi a do 4.6 por engano do lado Kotlin, e nenhum teste
  // reprovava: os dois casam pelo mesmo prefixo ate a fronteira.
  it('nao confunde sonnet 5 com sonnet 4-6', () => {
    expect(resolvePricing('claude-sonnet-5')?.inputMicrosPerMillion).toBe(2_000_000n);
    expect(resolvePricing('claude-sonnet-4-6')?.inputMicrosPerMillion).toBe(3_000_000n);
  });

  it('casa o prefixo com fronteira em hifen', () => {
    expect(resolvePricing('claude-opus-5')?.prefix).toBe('claude-opus-5');
    expect(resolvePricing('claude-opus-5-20260101')?.prefix).toBe('claude-opus-5');
    // Sem a fronteira, este cairia na tarifa do opus 5 em silencio.
    expect(resolvePricing('claude-opus-5x')).toBeNull();
  });

  it('prefere o prefixo mais longo', () => {
    // A ordem declarada na tabela e a do arquivo Kotlin, para o parser de paridade
    // comparar posicao a posicao. A ordem de match e outra, e e esta.
    expect(resolvePricing('claude-opus-4-5')?.prefix).toBe('claude-opus-4-5');
  });

  it('trata o marcador sintetico como preco zero conhecido', () => {
    const pricing = resolvePricing('<synthetic>');

    expect(pricing).not.toBeNull();
    expect(pricing?.inputMicrosPerMillion).toBe(0n);
    expect(isSyntheticModel('<synthetic>')).toBe(true);
    expect(isSyntheticModel('claude-opus-5')).toBe(false);
  });

  // Zero afirmaria que o turno nao custou nada, e tornaria o sintetico —
  // que e zero **conhecido** — indistinguivel do desconhecido.
  it('devolve custo indisponivel para modelo nao reconhecido', () => {
    expect(resolvePricing('gpt-5')).toBeNull();
    expect(resolvePricing(null)).toBeNull();
    expect(resolvePricing(undefined)).toBeNull();
    expect(resolvePricing('')).toBeNull();
  });

  it('soma os produtos antes da divisao unica', () => {
    const pricing = resolvePricing('claude-sonnet-5');
    expect(pricing).not.toBeNull();

    // 1M input a 2 USD/M + 1M output a 10 USD/M = 12 USD = 12_000_000 micros.
    const cost = costMicros(pricing!, {
      inputTokens: 1_000_000,
      outputTokens: 1_000_000,
      cacheReadTokens: 0,
      cacheWrite5mTokens: 0,
      cacheWrite1hTokens: 0,
    });

    expect(cost).toBe(12_000_000n);
  });

  it('cobra as tres modalidades de cache pela tarifa derivada', () => {
    const pricing = resolvePricing('claude-sonnet-5')!;

    const cost = costMicros(pricing, {
      inputTokens: 0,
      outputTokens: 0,
      cacheReadTokens: 1_000_000,
      cacheWrite5mTokens: 1_000_000,
      cacheWrite1hTokens: 1_000_000,
    });

    expect(cost).toBe(200_000n + 2_500_000n + 4_000_000n);
  });

  /**
   * A regressao que um `number` introduziria em silencio.
   *
   * 10^11 tokens de cache read a 200_000 micros por milhao da 2 x 10^16 na soma
   * ponderada — acima de `Number.MAX_SAFE_INTEGER` (~9,0 x 10^15). Num time de
   * verdade esse volume aparece em sete dias.
   */
  it('nao perde exatidao acima de 2^53', () => {
    const pricing = resolvePricing('claude-sonnet-5')!;
    const cacheReadTokens = 100_000_000_000;

    const cost = costMicros(pricing, {
      inputTokens: 0,
      outputTokens: 0,
      cacheReadTokens,
      cacheWrite5mTokens: 0,
      cacheWrite1hTokens: 0,
    });

    // 10^11 * 200_000 / 10^6 = 2 x 10^10 micros = 20.000 USD, exato.
    expect(cost).toBe(20_000_000_000n);
    expect(microsToUsdString(cost)).toBe('20000.000000');
    // A mesma conta em ponto flutuante ja nao e representavel: o produto passa de
    // MAX_SAFE_INTEGER e o resultado deixa de ser confiavel.
    expect(cacheReadTokens * 200_000 > Number.MAX_SAFE_INTEGER).toBe(true);
  });

  it('formata micros em dolares com seis casas', () => {
    expect(microsToUsdString(0n)).toBe('0.000000');
    expect(microsToUsdString(1n)).toBe('0.000001');
    expect(microsToUsdString(12_000_000n)).toBe('12.000000');
    expect(microsToUsdString(1_234_567n)).toBe('1.234567');
  });
});
