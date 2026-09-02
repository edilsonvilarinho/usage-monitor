import {
  CACHE_MULTIPLIERS,
  MODEL_PRICING,
  SYNTHETIC_MODEL_ID,
  type ModelPricingEntry,
} from './modelPricing.js';

/**
 * Aritmetica de preco, espelhada de
 * `src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricing.kt`.
 *
 * **O servidor continua nao precificando nas rotas de relatorio**: `/v1/report/*`
 * devolve linhas cruas e `/v1/pricing` publica a tabela, para o consumidor aplicar
 * a conta. Este modulo existe por causa do `/metrics`: o Prometheus nao aplica
 * tabela de preco nenhuma — ele ingere numeros —, entao ou o custo sai daqui, ou
 * quem raspa mantem uma copia da tabela a mao, que e exatamente o que a #105
 * provou divergir.
 *
 * A tabela continua tendo **um dono**, o Kotlin, e `tools/ci/check-pricing-parity.mjs`
 * compara os dois arquivos nos dois workflows. O que este modulo acrescenta e a
 * aritmetica, e a equivalencia dela com o original e afirmada por teste com os
 * mesmos numeros do `ModelPricingTableTest`.
 */

const TOKENS_PER_MILLION = 1_000_000n;

/** Tarifas derivadas de uma entrada da tabela, todas em micros por milhao. */
export interface ResolvedPricing {
  readonly prefix: string;
  readonly inputMicrosPerMillion: bigint;
  readonly outputMicrosPerMillion: bigint;
  readonly cacheReadMicrosPerMillion: bigint;
  readonly cacheWrite5mMicrosPerMillion: bigint;
  readonly cacheWrite1hMicrosPerMillion: bigint;
}

/** As cinco contagens de token de um turno, ou de um grupo deles ja somado. */
export interface TokenCounts {
  readonly inputTokens: number;
  readonly outputTokens: number;
  readonly cacheReadTokens: number;
  readonly cacheWrite5mTokens: number;
  readonly cacheWrite1hTokens: number;
}

/**
 * Ordem de match: prefixo mais longo primeiro.
 *
 * A ordem declarada em `MODEL_PRICING` e a do arquivo Kotlin, porque o parser de
 * paridade compara posicao a posicao — ela **nao** e a ordem de match, e
 * `MODEL_MATCH_RULE` publica isso. Ordenar aqui, uma vez, e o equivalente do
 * `sortedByDescending` que o Kotlin faz em tempo de execucao.
 */
const MATCH_ORDER: ReadonlyArray<ModelPricingEntry> = [...MODEL_PRICING].sort(
  (a, b) => b.prefix.length - a.prefix.length,
);

/**
 * Fronteira em `-`: `claude-sonnet-5` casa `claude-sonnet-5` e
 * `claude-sonnet-5-20260101`, e **nao** casa `claude-sonnet-5x`. Sem a fronteira,
 * um modelo novo com nome parecido herdaria a tarifa errada em silencio.
 */
function matchesPrefix(model: string, prefix: string): boolean {
  if (model === prefix) {
    return true;
  }
  return model.startsWith(`${prefix}-`);
}

/**
 * A tarifa de um modelo, ou `null` quando ele nao e reconhecido.
 *
 * **Nunca zero para desconhecido.** Zero afirmaria que o turno nao custou nada, e
 * o `<synthetic>` — que e preco zero **conhecido** — deixaria de ser distinguivel
 * de um modelo que a tabela nao conhece.
 */
export function resolvePricing(model: string | null | undefined): ResolvedPricing | null {
  if (model === null || model === undefined || model === '') {
    return null;
  }

  const entry = MATCH_ORDER.find((candidate) => matchesPrefix(model, candidate.prefix));
  if (entry === undefined) {
    return null;
  }

  const input = BigInt(entry.inputMicrosPerMillion);
  return {
    prefix: entry.prefix,
    inputMicrosPerMillion: input,
    outputMicrosPerMillion: BigInt(entry.outputMicrosPerMillion),
    cacheReadMicrosPerMillion: applyMultiplier(input, CACHE_MULTIPLIERS.read),
    cacheWrite5mMicrosPerMillion: applyMultiplier(input, CACHE_MULTIPLIERS.write5m),
    cacheWrite1hMicrosPerMillion: applyMultiplier(input, CACHE_MULTIPLIERS.write1h),
  };
}

function applyMultiplier(
  input: bigint,
  multiplier: { readonly numerator: number; readonly denominator: number },
): bigint {
  return (input * BigInt(multiplier.numerator)) / BigInt(multiplier.denominator);
}

/**
 * Custo em micros de USD.
 *
 * **BigInt, e nao `number`.** O Kotlin usa `Long`; em JS o `number` e um double e
 * perde exatidao acima de 2^53 (~9,0 x 10^15). A soma ponderada estoura isso com
 * folga num time de verdade: 10^11 tokens de cache read a 5 x 10^6 micros por
 * milhao ja da 5 x 10^17. Sem BigInt o custo sairia errado **sem erro nenhum**.
 *
 * Os produtos sao somados **antes** da divisao unica, como no original: dividir
 * por componente acumularia truncamento cinco vezes.
 */
export function costMicros(pricing: ResolvedPricing, tokens: TokenCounts): bigint {
  const weighted =
    BigInt(tokens.inputTokens) * pricing.inputMicrosPerMillion +
    BigInt(tokens.outputTokens) * pricing.outputMicrosPerMillion +
    BigInt(tokens.cacheReadTokens) * pricing.cacheReadMicrosPerMillion +
    BigInt(tokens.cacheWrite5mTokens) * pricing.cacheWrite5mMicrosPerMillion +
    BigInt(tokens.cacheWrite1hTokens) * pricing.cacheWrite1hMicrosPerMillion;
  return weighted / TOKENS_PER_MILLION;
}

/**
 * Micros em dolares, com seis casas fixas.
 *
 * A conversao acontece **so na exposicao**, e a partir do inteiro: dividir por
 * 10^6 em ponto flutuante antes de somar reintroduziria o erro que o BigInt
 * existe para evitar. Seis casas porque micro e a menor unidade que a tabela
 * distingue — nem uma a mais, que sugeriria precisao que o dado nao tem.
 */
export function microsToUsdString(micros: bigint): string {
  const negative = micros < 0n;
  const absolute = negative ? -micros : micros;
  const whole = absolute / TOKENS_PER_MILLION;
  const fraction = absolute % TOKENS_PER_MILLION;
  return `${negative ? '-' : ''}${whole}.${fraction.toString().padStart(6, '0')}`;
}

/** O marcador sintetico e preco zero **conhecido**, e continua sendo turno precificado. */
export function isSyntheticModel(model: string | null | undefined): boolean {
  return model === SYNTHETIC_MODEL_ID;
}
