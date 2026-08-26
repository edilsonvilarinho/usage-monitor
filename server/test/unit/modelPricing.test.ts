import { describe, expect, it } from 'vitest';
import {
  CACHE_MULTIPLIERS,
  MODEL_PRICING,
  PRICING_VERSION,
  SYNTHETIC_MODEL_ID,
} from '../../src/domain/modelPricing.js';

// A paridade com `ModelPricingTable.kt` e afirmada por
// `tools/ci/check-pricing-parity.mjs`, que le os dois arquivos. Aqui ficam as
// invariantes que aquele script nao ve, porque nao existem do lado Kotlin.
describe('tabela de precos publicada', () => {
  it('não repete prefixo', () => {
    const prefixes = MODEL_PRICING.map((entry) => entry.prefix);

    expect(new Set(prefixes).size).toBe(prefixes.length);
  });

  // `SYNTHETIC_MODEL_ID` e a constante que a rota publica; o literal `<synthetic>`
  // esta na lista porque o formato dela e contrato do parser de paridade. São dois
  // donos do mesmo texto, e é este teste que os mantém iguais.
  it('traz o marcador sintético como preço zero conhecido', () => {
    const synthetic = MODEL_PRICING.find((entry) => entry.prefix === SYNTHETIC_MODEL_ID);

    expect(synthetic).toBeDefined();
    expect(synthetic?.inputMicrosPerMillion).toBe(0);
    expect(synthetic?.outputMicrosPerMillion).toBe(0);
  });

  // Micros: fracao aqui viraria custo com casa decimal do lado do consumidor, que
  // e exatamente o que a unidade inteira existe para evitar.
  it('publica as tarifas como inteiros não negativos', () => {
    for (const entry of MODEL_PRICING) {
      expect(Number.isSafeInteger(entry.inputMicrosPerMillion)).toBe(true);
      expect(Number.isSafeInteger(entry.outputMicrosPerMillion)).toBe(true);
      expect(entry.inputMicrosPerMillion).toBeGreaterThanOrEqual(0);
      expect(entry.outputMicrosPerMillion).toBeGreaterThanOrEqual(0);
    }
  });

  it('publica os multiplicadores de cache como razões inteiras positivas', () => {
    for (const multiplier of Object.values(CACHE_MULTIPLIERS)) {
      expect(Number.isSafeInteger(multiplier.numerator)).toBe(true);
      expect(Number.isSafeInteger(multiplier.denominator)).toBe(true);
      expect(multiplier.numerator).toBeGreaterThan(0);
      expect(multiplier.denominator).toBeGreaterThan(0);
    }
  });

  // O consumidor guarda esta string junto do custo que calculou. Texto livre ali
  // tornaria duas coletas incomparaveis.
  it('versiona a tabela por data ISO', () => {
    expect(PRICING_VERSION).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
