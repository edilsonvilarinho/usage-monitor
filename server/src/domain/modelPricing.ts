/**
 * Tabela de precos por modelo, publicada como dado.
 *
 * O servidor continua **nao precificando**: ele publica a tabela e a regra de
 * match, e quem consome aplica a aritmetica. Uma tabela copiada a mao pelo
 * consumidor diverge na proxima mudanca de preco, e o painel e o PDF passam a
 * mostrar custos diferentes do mesmo periodo -- foi exatamente isso que a #105
 * mostrou acontecer dentro do proprio app.
 *
 * A fonte de verdade continua sendo
 * `src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricingTable.kt`.
 * `tools/ci/check-pricing-parity.mjs` compara os dois arquivos e reprova
 * divergencia, nos dois workflows de CI.
 *
 * **O FORMATO DESTA LISTA E CONTRATO.** O parser de paridade le por regex: uma
 * entrada por linha, na forma
 * `{ prefix: '<id>', inputMicrosPerMillion: <n>, outputMicrosPerMillion: <n> },`
 * e na mesma ordem do arquivo Kotlin. Reformatar quebra o script -- de
 * proposito: falha ruidosa vale mais que divergencia silenciosa.
 */

/** Uma entrada da tabela: prefixo do identificador e as duas tarifas base. */
export interface ModelPricingEntry {
  /** Prefixo do identificador de modelo. Casa exato ou seguido de `-`. */
  readonly prefix: string;
  readonly inputMicrosPerMillion: number;
  readonly outputMicrosPerMillion: number;
}

/** Razao inteira aplicada sobre a tarifa de input. */
export interface CacheMultiplier {
  readonly numerator: number;
  readonly denominator: number;
}

/**
 * Data da ultima revisao da tabela, publicada junto dela.
 *
 * O consumidor guarda esta string com o que calculou: sem ela, um custo antigo e
 * um novo do mesmo periodo sao indistinguiveis. Sobe a cada mudanca de preco.
 */
export const PRICING_VERSION = '2026-08-25';

/**
 * Marcador que o Claude Code usa em mensagens injetadas localmente.
 *
 * **Preco zero conhecido, nao preco desconhecido.** Trata-lo como desconhecido
 * marcaria sessoes inteiras como custo incompleto.
 */
export const SYNTHETIC_MODEL_ID = '<synthetic>';

/**
 * Regra de match, publicada como texto porque ela nao e derivavel da lista.
 *
 * Modelo nao reconhecido devolve custo **indisponivel**, nunca zero: e o
 * `forModel() == null` do Kotlin virando contrato.
 */
export const MODEL_MATCH_RULE =
  "prefixo com fronteira em '-', mais longo primeiro; nao reconhecido => custo indisponivel";

/**
 * Uma linha por entrada de `ModelPricingTable.kt`, na mesma ordem.
 *
 * A ordem declarada nao e a ordem de match: o Kotlin ordena por comprimento
 * decrescente em tempo de execucao, e `MODEL_MATCH_RULE` diz isso. Manter a
 * ordem do arquivo e o que permite ao parser de paridade comparar posicao a
 * posicao em vez de tolerar reordenacao silenciosa.
 */
export const MODEL_PRICING: ReadonlyArray<ModelPricingEntry> = [
  { prefix: 'claude-opus-5', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },
  { prefix: 'claude-opus-4-8', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },
  { prefix: 'claude-opus-4-7', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },
  { prefix: 'claude-opus-4-6', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },
  { prefix: 'claude-opus-4-5', inputMicrosPerMillion: 5_000_000, outputMicrosPerMillion: 25_000_000 },
  { prefix: 'claude-sonnet-5', inputMicrosPerMillion: 2_000_000, outputMicrosPerMillion: 10_000_000 },
  { prefix: 'claude-sonnet-4-6', inputMicrosPerMillion: 3_000_000, outputMicrosPerMillion: 15_000_000 },
  { prefix: 'claude-sonnet-4-5', inputMicrosPerMillion: 3_000_000, outputMicrosPerMillion: 15_000_000 },
  { prefix: 'claude-haiku-4-5', inputMicrosPerMillion: 1_000_000, outputMicrosPerMillion: 5_000_000 },
  { prefix: 'claude-fable-5', inputMicrosPerMillion: 10_000_000, outputMicrosPerMillion: 50_000_000 },
  { prefix: 'claude-mythos-5', inputMicrosPerMillion: 10_000_000, outputMicrosPerMillion: 50_000_000 },
  { prefix: '<synthetic>', inputMicrosPerMillion: 0, outputMicrosPerMillion: 0 },
];

/**
 * Os tres precos de cache derivam da tarifa de input.
 *
 * **Razoes inteiras, nao decimais.** A aritmetica do Kotlin
 * (`ModelPricing.kt:26-36`) e inteira em micros e nao trunca nesses valores;
 * publicar `0.1` e `1.25` como float convidaria o consumidor a introduzir erro
 * de ponto flutuante que o original nao tem.
 */
export const CACHE_MULTIPLIERS: Readonly<Record<'read' | 'write5m' | 'write1h', CacheMultiplier>> = {
  read: { numerator: 1, denominator: 10 },
  write5m: { numerator: 5, denominator: 4 },
  write1h: { numerator: 2, denominator: 1 },
};
