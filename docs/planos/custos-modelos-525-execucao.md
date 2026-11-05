# Custos dos modelos atuais do Claude — issue #525

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-11-05, branch
`fix/issue-525-model-pricing`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

`claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1` e `claude-mythos-5-1` herdam em silêncio a tarifa da versão
anterior, porque o match por prefixo aceita versão menor como se fosse sufixo de data. `claude-haiku-5-5` sai sem preço.
Corrigir as tarifas, restringir o match ao sufixo de data e modelar a faixa de prompt do Haiku 5.5, que vale por
requisição e por isso não pode ser aplicada sobre soma de turnos.

## Fatos confirmados (2026-11-05, `f1309744`)

- Tarifas da issue conferidas em `https://platform.claude.com/docs/en/about-claude/pricing` (WebFetch): Opus 5.5 4/20,
  write 5m 5, write 1h 8, read 0,20; Sonnet 5.5 2/10, read 0,10; Fable 5.1 e Mythos 5.1 10/50, read 0,25; Haiku 5.5 até
  100.000 tokens 0,10/0,50, read 0,01, acima 0,50/2,50, read 0,05. Fable 5, Mythos 5, Opus 5 e Sonnet 5 seguem em 0,1x.
- Nas tarifas acima só o cache read foge da derivação: write 5m é 1,25x e write 1h é 2x em todas, e as duas faixas do
  Haiku 5.5 são 0,1x. Divisores inteiros exatos: 20 (Opus 5.5, Sonnet 5.5) e 40 (Fable 5.1, Mythos 5.1).
- Regra da faixa, seção "Long context pricing" da mesma página: o tamanho do prompt conta todo o input, inclusive cache
  read e cache write; cada requisição é precificada sozinha, e a que passa do limite paga a tarifa alta inteira.
- Janela de 1M para Haiku 5.5, Opus 5.5, Sonnet 5.5 e Fable 5.1: `https://platform.claude.com/docs/en/about-claude/models/overview`.
  Mythos 5.1 fora dessa tabela; 1M vem de "Claude 4.6 and later models (except Claude Haiku 5.5)" em "Long context pricing".
- `matchesModelPrefix` é compartilhado: `ModelContextWindow.kt:30` usa o mesmo match. Restringi-lo tira a janela de
  `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1` e `claude-mythos-5-1` se eles não entrarem na tabela de janelas.
- Precificam **um turno**: `CliSessionModels.kt:32-49`, `CliSessionContextStatus.kt:55-63`,
  `ComputeCliSessionAnalyticsUseCase.kt:94-107`. Precificam **soma de turnos**: `CliUsageBreakdown.kt:234-248`,
  `CliUsageActivity.kt:100-109`, `TeamUsageTrend.kt:108-119`, `WindowedSessionAccumulator.kt:65-76` e `metrics.ts:129-139`.
- As somas vêm de `SELECT_SESSIONS_SINCE_SQL`, `SELECT_USAGE_GROUPS_SQL`, `SELECT_HOURLY_USAGE_SQL`
  (`LocalCliSessionIndexSql.kt`) e, no servidor, de cinco `SELECT` com `SUM` em `teamRepository.ts`.
- `cli_sessions.cost_micros` só é regravado por `recomputeSession`, chamado para sessões de arquivo relido. Transcript
  apagado do disco nunca é relido: subir `INDEX_SCHEMA_VERSION` sozinho deixa essas sessões com custo antigo. Os tokens
  por turno ficam em `cli_turns`, então o recálculo não depende do arquivo.
- O cliente não consome `/v1/pricing`: nenhum DTO ou fetch em `src`. O servidor só aplica preço no `/metrics`.
- Sintoma "A verificar" da issue: `windowModel` é o `primaryModel` (`CliSessionModels.kt:161`), o modelo com mais turnos
  (`LocalCliSessionDataSource.kt`, `recomputeSession`). Com 200 turnos de Haiku 5.5 e 52 de Opus 5.5 o modelo é Haiku, ausente
  da tabela de janelas: daí `unknown model window`. Incluir o Haiku 5.5 resolve esse caso sem mudar a regra do `primaryModel`.

## Decisões

- **Tarifa de faixa é parte de `ModelPricing`**, em `longPrompt: LongPromptPricing?`. Nenhum valor novo de enum.
- **`costMicros` continua sendo a conta de uma tarifa, sem escolher faixa.** Código de produção chama `turnCostMicros`
  (um turno, escolhe a faixa pelos próprios tokens) ou `groupCostMicros` (soma, recebe a parte de prompt longo). Escolher
  a faixa dentro de `costMicros` faria uma soma de turnos pequenos cair na faixa alta sem erro nenhum.
- **Linha agregada carrega a parte de prompt longo ao lado do total**, em `longPrompt: UsageTokenCounts?`. Agrupar por
  faixa no `GROUP BY` foi recusado: duplicaria a linha `(sessão, modelo)`, que já é contrato de `/v1/team` e `/v1/report`.
- **`null` é "não medido".** Modelo com faixa e linha sem a parte longa conta em `unpricedTurnCount`, nunca na faixa baixa:
  é o que acontece com servidor de time antigo. Modelo sem faixa ignora o campo e não muda de custo.
- **Limite único** `LONG_PROMPT_THRESHOLD_TOKENS = 100_000L`, usado no SQL local e no do servidor. Outro modelo com outro
  limite exige mudar o SQL; a paridade compara a constante nos dois lados.
- **Colunas planas no servidor** (`longPromptInputTokens`…): as rotas devolvem a linha do SQL sem mapear, e o cliente
  monta `UsageTokenCounts` só quando as cinco chegam. Campo novo em rota existente não precisa de gate.
- **`INDEX_SCHEMA_VERSION` 4 recalcula de `cli_turns`, sem releitura de transcript.** O reset de arquivos continua só para
  quem vem de versão abaixo de 3.
- **`/v1/pricing` cresce só por adição.** `cacheMultipliers.read` fica como padrão; entrada que foge dele declara
  `cacheReadDenominator`; entrada com faixa declara `longPrompt`; a raiz publica `longPromptThresholdTokens`.
- **Fora:** multiplicador US-only e Batch, que não aparecem no transcript; regra do `primaryModel`; IDs de Bedrock
  (`anthropic.claude-…`), que já não casavam antes.

## Especificação das atividades

### A02 — tarifas, divisor, faixa, match e janelas

`ModelPricing.kt`: construtor ganha `cacheReadDivisor: Long = DEFAULT_CACHE_READ_DIVISOR` (`10L`, positivo) e
`longPrompt: LongPromptPricing? = null`. Novos no mesmo arquivo: `const val LONG_PROMPT_THRESHOLD_TOKENS = 100_000L`,
`data class LongPromptPricing(val thresholdTokens: Long, val pricing: ModelPricing)`,
`fun forPrompt(promptTokens: Long): ModelPricing` (tarifa alta só com `promptTokens > thresholdTokens`).
`ofUsdPerMillion` ganha `cacheReadDivisor`, `longPromptInput: Double? = null`, `longPromptOutput: Double? = null`.

`ModelPricingTable.kt`, uma declaração por linha, formato lido pela paridade:

```kotlin
private val OPUS_5_5 = ModelPricing.ofUsdPerMillion(input = 4.00, output = 20.00, cacheReadDivisor = 20L)
private val SONNET_5_5 = ModelPricing.ofUsdPerMillion(input = 2.00, output = 10.00, cacheReadDivisor = 20L)
private val FABLE_5_1 = ModelPricing.ofUsdPerMillion(input = 10.00, output = 50.00, cacheReadDivisor = 40L)
private val HAIKU_5_5 = ModelPricing.ofUsdPerMillion(input = 0.10, output = 0.50, longPromptInput = 0.50, longPromptOutput = 2.50)
```

Entradas novas: `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-haiku-5-5`, `claude-fable-5-1`, `claude-mythos-5-1`,
cada uma logo antes da entrada da mesma família já existente, na ordem da lista.

`matchesModelPrefix`: casa exato, ou prefixo seguido de exatamente `-` e oito dígitos. Atualizar o KDoc da tabela.

`ModelContextWindow.kt`: `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1`, `claude-mythos-5-1` e
`claude-haiku-5-5` em 1M.

Testes em `ModelPricingTableTest.kt`, e janelas no teste vizinho que já cobre a tabela: cada modelo da tabela oficial casa
com a própria entrada; `claude-opus-5-6` e `claude-opus-5-5-beta` devolvem `null`; `claude-haiku-4-5-20251001` casa;
`claude-opus-5-5-20270101` casa com Opus 5.5; as cinco tarifas de cada modelo novo, em micros; Opus 5.5 com input 100,
output 49.360, cache read 8.405.121 e write 1h 218.174 dá `4_414_016` em `costMicros`; `forPrompt(100_000)` baixa,
`forPrompt(100_001)` alta.

### A03 — custo por turno

`ModelPricing.kt`: `turnCostMicros(...)` e `turnCacheSavingsMicros(...)`, que escolhem a faixa pelo prompt
(`input + cacheRead + cacheWrite5m + cacheWrite1h`) e chamam `costMicros`/`cacheSavingsMicros` da faixa.

Consumidores: `CliSessionModels.kt:32-49` (custo e economia do turno), `ComputeCliSessionAnalyticsUseCase.kt:94-107`
(`forPrompt` por turno) e `CliSessionContextStatus.kt:55-63` (`forPrompt(liveContextTokens)`: o contexto vivo é o prompt da
próxima mensagem).

Testes: turno de Haiku 5.5 com 100.000 e 100.001 tokens de prompt, com o prompt repartido entre input, cache read e cache
write; `computeContextStatus` e o breakdown da análise nas duas faixas. Output fora do prompt: 100.000 de prompt com
500.000 de output continua na faixa baixa.

### A04 — custo de grupo no domain

`ModelPricing.kt`: `data class UsageTokenCounts(inputTokens, outputTokens, cacheReadTokens, cacheWrite5mTokens,
cacheWrite1hTokens)`, todos `Long = 0L`, com `promptTokens`. `fun longPromptPartOfTurn(tokens: UsageTokenCounts):
UsageTokenCounts` (o próprio turno acima do limite, zeros abaixo). `fun groupCostMicros(total: UsageTokenCounts,
longPrompt: UsageTokenCounts?): Long?` e `fun groupCacheSavingsMicros(...)`: sem faixa, a conta de sempre sobre o total;
com faixa e `longPrompt == null`, `null`; com faixa e parte longa, `(total - parte)` na tarifa baixa mais a parte na alta.

`CliUsageGroupRow`, `CliHourlyUsageRow` e `TeamTrendRow` ganham `longPrompt: UsageTokenCounts? = null`.
`WindowedSessionAccumulator.addModelGroup` ganha `longPrompt: UsageTokenCounts? = null` e repassa ao `CliUsageGroupRow`.
`CliUsageBreakdown.kt`, `CliUsageActivity.kt`, `TeamUsageTrend.kt` e `WindowedSessionAccumulator.kt` chamam
`groupCostMicros`; resultado `null` segue o caminho de modelo sem tarifa daquele arquivo.

Testes, um por consumidor: grupo de Haiku 5.5 com parte longa dá a soma das duas faixas; sem parte longa fica sem preço;
modelo sem faixa não muda com ou sem parte longa. Caso da issue: 76 turnos baixos e 124 altos não dão o mesmo que o total
na tarifa baixa.

### A05 — SQL local e `INDEX_SCHEMA_VERSION` 4

`LocalCliSessionIndexSql.kt`: fragmento único com as cinco colunas `SUM(CASE WHEN <prompt> > $LONG_PROMPT_THRESHOLD_TOKENS
THEN <coluna> ELSE 0 END) AS long_prompt_<coluna>`, usado em `SELECT_SESSIONS_SINCE_SQL`, `SELECT_USAGE_GROUPS_SQL` e
`SELECT_HOURLY_USAGE_SQL`. Leitura em `LocalCliSessionDataSource.kt` e `LocalCliSessionUsageGroups.kt` monta
`UsageTokenCounts`. O `LocalCliSessionDataSource.kt` tem 713 linhas: a leitura comum sai para função em
`LocalCliSessionUsageGroups.kt`, sem passar de 750.

`INDEX_SCHEMA_VERSION = 4`, com a linha no histórico do KDoc. `migrateSchemaVersion`: reset de arquivos só com `current < 3`;
com `current < 4`, recalcula `cost_micros` e `unpriced_turns` de toda sessão a partir de `cli_turns`, numa transação, usando
`turnCostMicros`. O recálculo mora em `LocalCliSessionIndexSchema.kt`.

Testes em arquivo novo `LocalCliSessionPricingMigrationTest.kt`: índice na versão 3 com sessão de Opus 5.5 e custo antigo
gravado, sem transcript em disco, sai com custo novo; `cli_session_files` intacto vindo da versão 3; índice já na versão 4 não
muda. Grupo de Haiku 5.5 lido do SQL com turnos nas duas faixas tem a parte longa certa nos três `SELECT`.

### A06 — servidor e paridade

`server/src/domain/modelPricing.ts`: `PRICING_VERSION = '2026-11-05'`, `LONG_PROMPT_THRESHOLD_TOKENS = 100_000`,
`ModelPricingEntry` com `cacheReadDenominator?` e `longPrompt?`, `MODEL_MATCH_RULE` novo, entradas novas na ordem do Kotlin:

```ts
{ prefix: 'claude-opus-5-5', inputMicrosPerMillion: 4_000_000, outputMicrosPerMillion: 20_000_000, cacheReadDenominator: 20 },
{ prefix: 'claude-haiku-5-5', inputMicrosPerMillion: 100_000, outputMicrosPerMillion: 500_000, longPrompt: { inputMicrosPerMillion: 500_000, outputMicrosPerMillion: 2_500_000 } },
```

`usageCost.ts`: `matchesPrefix` com a mesma regra de data; `ResolvedPricing.longPrompt: ResolvedPricing | null`;
`groupCostMicros(pricing, row)` no molde do Kotlin. `metrics.ts` usa essa função; `null` vai para a série de turnos sem preço.

`teamRepository.ts`: fragmento único com as cinco colunas `longPromptInputTokens`, `longPromptOutputTokens`,
`longPromptCacheReadTokens`, `longPromptCacheWrite5mTokens`, `longPromptCacheWrite1hTokens`, nos cinco `SELECT` agregados,
tipos de linha atualizados. Calculadas na leitura com `SUM(CASE WHEN …)` sobre `team_turns`, como no A05: sem coluna nova,
migração ou mudança no ingest. `report.ts` publica `longPromptThresholdTokens` no `/v1/pricing`.

`tools/ci/check-pricing-parity.mjs`: compara divisor de cache read por entrada, faixa por entrada e o limite. O multiplicador
`read` passa a sair de `DEFAULT_CACHE_READ_DIVISOR`. Teste novo `tools/ci/test/check-pricing-parity.test.mjs`: roda o script
sobre cópia dos três arquivos em pasta temporária; verde sem mudança, saída 1 com divisor, faixa ou limite divergente.

Testes do servidor: `server/test/unit/modelPricing.test.ts` e o teste vizinho de `usageCost` com os mesmos números do Kotlin;
`/metrics` com Haiku 5.5 nas duas faixas; `/v1/team` e `/v1/report` devolvem as colunas novas.

### A07 — DTO e mapper do time

`TeamUsageRowDto` e `TeamTrendRowDto`: cinco campos `longPrompt…: Long? = null`. Mapper monta `UsageTokenCounts` quando os
cinco chegam, senão `null`, e repassa a `addModelGroup`, `CliUsageGroupRow` e `TeamTrendRow`. O laço por turno em
`TeamUsageMapper.kt:247` usa `longPromptPartOfTurn`.

Testes em `TeamUsageMapperTest.kt`: resposta de servidor antigo, sem os campos, deixa Haiku 5.5 sem preço e Opus 5.5 precificado;
resposta nova precifica as duas faixas; detalhe de sessão com turnos nas duas faixas soma o mesmo que `turnCostMicros`.

### A08 — documentação

`docs/integrations.md` ou `docs/presentation.md` onde o custo é descrito, `docs/team-integration.md` e `server/README.md`
(`/v1/pricing` e colunas novas). Sem harness: a linha registra o `git diff --stat` e a releitura.

## Atividades

| Atividade | Conteúdo | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Tarifas, divisor, faixa, match e janelas | `backend-dev` |
| A03 | Custo por turno | `backend-dev` |
| A04 | Custo de grupo no domain | `backend-dev` |
| A05 | SQL local e `INDEX_SCHEMA_VERSION` 4 | `backend-dev` |
| A06 | Servidor, `/metrics`, `/v1/pricing` e paridade | `backend-dev` |
| A07 | DTO e mapper do time | `backend-dev` |
| A08 | Documentação | Neferpitou |

A06 depende só do formato do A02 e corre em paralelo com A03–A05: toca `server/` e `tools/ci/`, sem suíte em comum.

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano | Sem harness: fatos medidos por `WebFetch` da página de preços e leitura do código em `f1309744`. |
| A02 | Tarifas, `cacheReadDivisor`, `LongPromptPricing`, `forPrompt`, match por sufixo de data e janelas | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"` com stub, 497 testes, 14 falhas por `AssertionError` em `ModelPricingTableTest.kt:195…337` e `ModelContextWindowTableTest.kt:16,26`; o log do Gradle não trouxe expected/actual. Verde: mesmo comando, 497 testes, 0 falhas; `ModelPricingTableTest` 35 testes, 0 falhas depois da devolução 1/3 (declaração de `HAIKU_5_5` em linha única). |
| A03 | `turnCostMicros`, `turnCacheSavingsMicros`; turno, contexto vivo e breakdown da análise pela faixa do próprio prompt | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"` com stub, `ModelPricingTurnTest.kt:44` `expected:<28751> but was:<0>`, `:93` `expected:<28751> but was:<5750>`, contexto em 100.001 `expected:<5000> but was:<1000>`, breakdown em 100.001 `expected:<20000> but was:<4000>`. Verde: mesmo comando, 507 testes, 0 falhas, depois da devolução 1/3 (breakdown da análise ficara de fora). |
| A04 | `UsageTokenCounts`, `longPromptPartOfTurn`, `groupCostMicros`, `groupCacheSavingsMicros`; `longPrompt` nas três linhas agregadas e nos quatro consumidores | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"` com stub, 16 falhas por asserção: `expected:<13128000> but was:<2960000>` nas duas faixas (grupo e quatro consumidores), `expected:<0> but was:<2960000>` sem parte longa. Verde: mesmo comando, 526 testes, 0 falhas; `LongPromptGroupCostTest` 9, `LongPromptConsumersTest` 10. Devolução 1/3: KDocs falsos e nomes de teste. |
| A05 | Colunas `long_prompt_*` nos três `SELECT`, leitura de `UsageTokenCounts`, `INDEX_SCHEMA_VERSION` 4 com recálculo em transação a partir de `cli_turns` | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*"`, migração `expected:<77500> but was:<0>`, grupos `expected:<UsageTokenCounts(inputTokens=150000, outputTokens=1000, …)> but was:<null>`. Verde: mesmo comando, 618 testes, 0 falhas na primeira entrega; depois da devolução 1/3, `LocalCliSessionPricingMigrationTest` 6 de 6, saída 0. Os testes de transcript apagado, `cli_session_files` intacto, versão 4 sem recálculo e `readSessions` nasceram com a produção pronta e passaram de primeira: sem vermelho próprio. A transação não tem asserção; validada por leitura do diff. |
| A06 | Tabela, `PRICING_VERSION` 2026-11-05, match por data, `groupCostMicros`, colunas `longPrompt*` nos cinco `SELECT`, `/metrics`, `/v1/pricing` e paridade | Vermelho: `npm test` com `expected '2026-08-25' to be '2026-11-05'`, `expected 500000n to be 200000n`, `expected undefined to be 100000`, `expected 'usage_monitor_cost_usd_window{account…' to contain ' 0.500000'`; paridade `actual: 1, expected: 0`. Verde: `node tools/ci/check-pricing-parity.mjs` saída 0, 17 modelos; `node --test "tools/ci/test/*.test.mjs"` 19 de 19; `cd server && npm test` 327 testes em 24 arquivos; `npx tsc --noEmit -p tsconfig.json` saída 0. Devolução 1/3: `metrics.ts`, `teamRepository.ts` e `report.ts` tinham ficado de fora. |
| A07 | Cinco campos `longPrompt*` em `TeamUsageRowDto` e `TeamTrendRowDto`; mapper monta a parte longa só com os cinco; detalhe usa `longPromptPartOfTurn` | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*"`, `TeamUsageMapperTest.kt:497` `expected:<UsageTokenCounts(inputTokens=1000000, …)> but was:<null>`, `:460` `expected:<600000> but was:<0>`, `:478` `expected:<505000> but was:<0>`. Os dois testes de servidor antigo passaram de primeira: guardam o comportamento já dado pelo `null`. Verde: mesmo comando, 626 testes, 0 falhas; `TeamUsageMapperTest` 25 de 25. |
| A08 | `server/README.md`, `docs/team-integration.md`, `docs/presentation.md` e regra no `CLAUDE.md` | Sem harness: `git diff --stat` dos quatro arquivos e releitura; `/v1/team/trend` conferido em `teamRepository.ts`, `...rest` repassa as colunas. `CHANGELOG.md` fora: sem entrada desde o #190. |

## Problemas em aberto e riscos

- Entre A02 e A04 o branch precifica grupos de Haiku 5.5 na faixa baixa, e entre A04 e A05 os deixa sem preço. Nenhum desses
  commits sai sozinho para a `main`: o PR mescla o conjunto.
- Servidor de time antigo deixa Haiku 5.5 sem preço nos agregados até ser atualizado. É a decisão registrada acima.
- Quem consome `/v1/report/*` e aplica a tabela por conta própria precisa das colunas novas para o Haiku 5.5.

## Desvios do plano e achados da execução

- A02: o match passou a exigir sufixo de oito dígitos, e um ID com sufixo textual deixaria de casar. Medido nos transcripts
  locais (`grep -rhoE '"model":"[^"]+"' ~/.claude/projects`, 2026-11-05): `claude-opus-5-5` 24.292, `claude-sonnet-5-5` 6.211,
  `claude-haiku-5-5` 4.191, `claude-sonnet-5` 129, `<synthetic>` 39, `sonnet` 8. Nenhum sufixo textual; `sonnet` já não casava.
  A amostra é desta máquina, não de toda instalação.
- A06, defeito anterior a esta issue e fora dela: acima de `TEAM_METRICS_MAX_SERIES`, `mergeModels` (`metrics.ts`) grava
  `model: null`, `resolvePricing(null)` devolve `null` e todo o custo da janela vai para a série de turnos sem preço. Confirmado
  por leitura; `model: null` já está na `main` em `f1309744`. Sem teste nem correção aqui.
- A02: `ofUsdPerMillion` recusa `longPromptInput` sem `longPromptOutput`, além do briefing; a tabela de janelas ganhou teste
  próprio, `ModelContextWindowTableTest.kt`, que não existia.
