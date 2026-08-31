# Issue #140 — projeção de uso: DeepSeek some da tela + verificação OpenCode Go/OpenRouter

## Contexto

Issue #140 mistura duas perguntas na descrição:

- **Título**: verificar se a projeção de uso foi desenvolvida para OpenCode Go e OpenRouter, e se
  está sendo cacheada.
- **Corpo + screenshots**: relato de que a projeção do DeepSeek "estava sendo calculada e
  apresentando, porém sumiu". O próprio dono comentou depois "no deepseek tem histórico" (com
  screenshot mostrando histórico presente no gráfico).

Investigação de código fechou dois achados distintos e confirmados (arquivo:linha lido, sem
suposição):

### Achado 1 — bug real: `DeepSeekHistoryCard` esconde o texto de previsão

`UsageForecast` (`domain/entity/UsageHistoryModels.kt`) tem 4 estados: `InsufficientData`,
`NoGrowth`, `ResetsBeforeExhaustion`, `EstimatedExhaustionAt`. O card genérico
(`HistorySeriesCard`, via `historyMetricEntries`) usa `forecastLabel()`
(`HistoryScreenFormatting.kt:98-111`), que **sempre** devolve um rótulo — inclusive "Dados
insuficientes" e "Sem crescimento detectado".

O card do DeepSeek é outro: `DeepSeekHistoryCard` (`HistoryScreen.kt:648-654`) chama
`deepSeekForecastText()` (`HistoryScreenFormatting.kt:149-162`), que devolvia `null` para
`InsufficientData`, `NoGrowth` e `ResetsBeforeExhaustion` — só produzia texto em
`EstimatedExhaustionAt`. Com `?.let { Text(...) }`, o `Text` inteiro desaparecia da tela nesses
três estados.

Como o saldo do DeepSeek é saldo pré-pago (`hasKnownResetAt = false`, `DeepSeekMapper.kt:35-36,49-50`),
o forecast dele só transita entre `InsufficientData` ↔ `NoGrowth` ↔ `EstimatedExhaustionAt` (nunca
`ResetsBeforeExhaustion`, que exige reset conhecido — `UsageHistoryRepositoryImpl.kt:219-227`).
Bastava o saldo não cair entre duas coletas (`positiveDelta <= 0L` → `NoGrowth`,
`UsageHistoryRepositoryImpl.kt:186-188`) para a frase sumir sem aviso nenhum — exatamente o relato
do usuário. Não é falta de histórico (o dono já confirmou que há histórico); era o card escondendo
o estado em vez de descrevê-lo.

### Achado 2 — OpenCode Go e OpenRouter: projeção já está desenvolvida, mas sem prova em teste

| | `periodEndAt` | `hasKnownResetAt` | Persistência | Rota na tela de histórico |
|---|---|---|---|---|
| **OpenRouter** (`OpenRouterMapper.kt:32-33`) | `Instant.DISTANT_FUTURE` | `false` | `isPersistableDashboardStats` (`DashboardViewModel.kt:801-809`) é `true` pra qualquer fonte que não seja Codex — grava a cada coleta | `HistorySeriesCard` genérico (não está em `isObservedActivitySource()`, `ApiSourcePresentation.kt:24-26`) |
| **OpenCode Go** (`OpenCodeGoMapper.kt:60-86`) | `resetsAt` real da API, ou sentinela `2100-01-01` se ausente | `true` quando há `resetsAt` válido, `false` na sentinela | idem acima | idem acima |

Ambos passam pelo mesmo `UsageHistoryRepositoryImpl.calculateForecast`/`currentSegment` que
DeepSeek/MiniMax/Codex/Anthropic — nenhum código novo precisa ser escrito para "desenvolver" a
projeção, ela já existe pra essas fontes desde que herdam o pipeline genérico. Não há camada de
cache própria pra elas (`RemoteApiDataSource.kt:221-238` — GET simples a cada ciclo de 10 min,
mesmo `DashboardCacheRepositoryImpl` genérico de todas as fontes) — a suspeita de "está sendo
cacheado" (impedindo dado novo) não se sustenta no código lido.

O gap real: **zero teste** hoje exercitava mapper→snapshot→`calculateForecast`→`riskSummary` pra
OpenRouter ou OpenCode Go (só havia teste de mapper isolado, `OpenRouterMapperTest.kt`/
`OpenCodeGoMapperTest.kt`, que não passam pelo `UsageHistoryRepositoryImpl`).
`UsageHistoryRepositoryImplTest.kt` já tinha 3 testes desse pipeline completo pro DeepSeek — não
tinha equivalente pras duas fontes novas.

## Atividades

- **A01** — Corrigir `DeepSeekHistoryCard`: previsão sempre visível. Código +
  `HistoryScreenFormattingTest.kt` no mesmo commit.
- **A02** — Teste E2E de forecast para OpenRouter em `UsageHistoryRepositoryImplTest.kt`.
- **A03** — Teste E2E de forecast para OpenCode Go em `UsageHistoryRepositoryImplTest.kt`.

## Ponto de situação

| Atividade | Status | Resultado | Comentário na issue |
|---|---|---|---|
| A01 — DeepSeekHistoryCard sempre mostra previsão | Concluída | `desktopTest --tests "com.usagemonitor.presentation.*"` verde (4 testes novos) | pendente |
| A02 — Teste E2E forecast OpenRouter | Pendente | — | — |
| A03 — Teste E2E forecast OpenCode Go | Pendente | — | — |

## Fora de escopo

- Não há evidência de cache indevido em OpenCode Go/OpenRouter — a hipótese do título não se
  confirmou no código lido. Fecha esse ponto da issue com o achado, não com mudança de código.
- Bug conhecido e documentado à parte (issue #109): Kilo e OpenCode (Zen grátis) gravam
  `periodEndAt = capturedAt`, o que quebra `currentSegment` pra eles. Não é o mesmo bug desta issue
  e tem issue própria — não entra aqui.
