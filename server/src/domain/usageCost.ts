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
const DATE_SUFFIX = /^\d{8}$/;
const DEFAULT_READ_DENOMINATOR = BigInt(CACHE_MULTIPLIERS.read.denominator);

/** As cinco tarifas de um preco, todas em micros por milhao. */
export interface ResolvedRates {
  readonly inputMicrosPerMillion: bigint;
  readonly outputMicrosPerMillion: bigint;
  readonly cacheReadMicrosPerMillion: bigint;
  readonly cacheWrite5mMicrosPerMillion: bigint;
  readonly cacheWrite1hMicrosPerMillion: bigint;
}

/** Tarifas derivadas de uma entrada da tabela, com a faixa de prompt longo quando ha. */
export interface ResolvedPricing extends ResolvedRates {
  readonly prefix: string;
  /** Faixa de prompt longo; `null` quando o modelo nao tem faixa. */
  readonly longPrompt: ResolvedRates | null;
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
 * Tokens de um grupo de requisicoes, separados pelo prompt de cada uma. A faixa de
 * prompt longo vale por requisicao: a parte longa vem aqui ja separada.
 */
export interface TokenGroup {
  readonly shortPrompt: TokenCounts;
  /** Parte das requisicoes com prompt acima do limite; ausente = nenhuma. */
  readonly longPrompt?: TokenCounts;
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
 * Fronteira em `-` seguida de exatamente oito digitos: `claude-sonnet-5` casa
 * `claude-sonnet-5` e `claude-sonnet-5-20260101`, e **nao** casa `claude-sonnet-5x`
 * nem `claude-sonnet-5-6`. Sem a fronteira, um modelo novo com nome parecido
 * herdaria a tarifa errada em silencio.
 */
function matchesPrefix(model: string, prefix: string): boolean {
  if (model === prefix) {
    return true;
  }
  if (!model.startsWith(`${prefix}-`)) {
    return false;
  }
  return DATE_SUFFIX.test(model.slice(prefix.length + 1));
}

/** Deriva as tarifas de cache a partir do input, como o `ModelPricing.kt`. */
function deriveRates(input: bigint, output: bigint, readDenominator: bigint): ResolvedRates {
  return {
    inputMicrosPerMillion: input,
    outputMicrosPerMillion: output,
    cacheReadMicrosPerMillion: input / readDenominator,
    cacheWrite5mMicrosPerMillion: applyMultiplier(input, CACHE_MULTIPLIERS.write5m),
    cacheWrite1hMicrosPerMillion: applyMultiplier(input, CACHE_MULTIPLIERS.write1h),
  };
}

function toResolved(entry: ModelPricingEntry): ResolvedPricing {
  const readDenominator =
    entry.cacheReadDenominator === undefined
      ? DEFAULT_READ_DENOMINATOR
      : BigInt(entry.cacheReadDenominator);
  const rates = deriveRates(
    BigInt(entry.inputMicrosPerMillion),
    BigInt(entry.outputMicrosPerMillion),
    readDenominator,
  );
  return {
    ...rates,
    prefix: entry.prefix,
    // Igual ao Kotlin: a faixa de prompt longo e construida com o divisor padrao.
    longPrompt:
      entry.longPrompt === undefined
        ? null
        : deriveRates(
            BigInt(entry.longPrompt.inputMicrosPerMillion),
            BigInt(entry.longPrompt.outputMicrosPerMillion),
            DEFAULT_READ_DENOMINATOR,
          ),
  };
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
  return toResolved(entry);
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
export function costMicros(pricing: ResolvedRates, tokens: TokenCounts): bigint {
  const weighted =
    BigInt(tokens.inputTokens) * pricing.inputMicrosPerMillion +
    BigInt(tokens.outputTokens) * pricing.outputMicrosPerMillion +
    BigInt(tokens.cacheReadTokens) * pricing.cacheReadMicrosPerMillion +
    BigInt(tokens.cacheWrite5mTokens) * pricing.cacheWrite5mMicrosPerMillion +
    BigInt(tokens.cacheWrite1hTokens) * pricing.cacheWrite1hMicrosPerMillion;
  return weighted / TOKENS_PER_MILLION;
}

function addCounts(a: TokenCounts, b: TokenCounts): TokenCounts {
  return {
    inputTokens: a.inputTokens + b.inputTokens,
    outputTokens: a.outputTokens + b.outputTokens,
    cacheReadTokens: a.cacheReadTokens + b.cacheReadTokens,
    cacheWrite5mTokens: a.cacheWrite5mTokens + b.cacheWrite5mTokens,
    cacheWrite1hTokens: a.cacheWrite1hTokens + b.cacheWrite1hTokens,
  };
}

/**
 * Custo de um grupo com a faixa de prompt longo aplicada por parte.
 *
 * Sem faixa no modelo, as duas partes somam na tarifa base. Com faixa, a parte curta
 * usa a base e a longa usa a faixa; **faixa declarada e parte longa ausente devolve
 * `null`**, nunca a tarifa baixa — o grupo nao tem como ser precificado sem ela.
 * Cada parte divide uma vez, entao o truncamento e de no maximo 1 micro por parte.
 */
export function groupCostMicros(pricing: ResolvedPricing, group: TokenGroup): bigint | null {
  if (pricing.longPrompt === null) {
    const counts =
      group.longPrompt === undefined ? group.shortPrompt : addCounts(group.shortPrompt, group.longPrompt);
    return costMicros(pricing, counts);
  }
  if (group.longPrompt === undefined) {
    return null;
  }
  return costMicros(pricing, group.shortPrompt) + costMicros(pricing.longPrompt, group.longPrompt);
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
