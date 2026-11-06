# Ordenação por coluna nas listas de sessões CLI (#537)

- **Modelo:** Claude Opus 5.5 (orquestração); implementação pelos agentes de `.claude/agents/`.
- **Ferramenta:** Claude Code.
- **Data:** 2026-11-06.
- **Branch:** `feat/issue-537-session-column-sort`.
- **Identidade de commit:** `claude <claude@anthropic.com>`, por comando.

## Objetivo

Clicar no cabeçalho de uma coluna numérica ordena a lista de sessões CLI. Vale para a lista Anthropic, a lista Codex e o
bloco de sessões do modal do time. Direção visual escolhida pelo usuário: **AM1 · Estrela-guia**, galeria em
`prototipos/issue-537/index.html` (fora do versionamento).

## Fatos confirmados

| Fato | Como foi medido |
|---|---|
| Nenhum cabeçalho de sessão é clicável; `AppColumnHeaderLabel` é só `Text` | leitura de `components/AppStructure.kt:556-571` |
| Só o Resumo ordena, com `remember` na tela e `AppSegmentedControl` | `CliUsageBreakdownPane.kt:151-152`, `:388-412` |
| A ordem atual vem da consulta: Anthropic por `last_ts DESC`, Codex por `lastTs` e `sessionId` | `LocalCliSessionIndexSql.kt:320-325`, `LocalCodexCliSessionDataSource.kt:123-125` |
| Lista de sessões não pagina nem tem `LIMIT`: ordenar no cliente vê todas as linhas | grep nas consultas; `LazyColumn` em `CliSessionsScreen.kt` e `CodexCliSessionsScreen.kt:262` |
| `activeMillis` e `throughput` são anuláveis; `tokensPerSecond` também | `domain/entity/CliSessionModels.kt`, `OutputThroughput.kt:20` |
| O bloco do time reusa `CliSessionColumnHeader` | `TeamUsageScreen.kt:611-614` |
| Rótulo "Tokens (with cache)" mede 114 px de 136 no mockup com IBM Plex Mono | `document.fonts.check` verdadeiro e medição na galeria |

## Decisões

1. **Ciclo do clique:** maior primeiro → menor primeiro → padrão. Clicar outra coluna começa em maior primeiro.
2. **Padrão** (`sort == null`) devolve a lista como publicada, sem reordenar: a ordem da consulta continua dona do caso comum.
3. **`null` fica sempre no fim**, nas duas direções: não medido não é zero.
4. **Ordem total:** empate cai em `lastTs` decrescente, depois `sessionId` crescente. Sem isso a tela recompõe a cada tique.
5. **Custo** compara `costMicros`, o número exibido. Sessão com turno sem tarifa (`+`) entra pelo valor parcial que a tela mostra.
6. **Cache** compara `cacheHitRate` no Anthropic e a mesma razão exibida no Codex.
7. **Estado** em `remember` na tela, como no Resumo. Não vai para o view model nem persiste entre aberturas.
8. **Time:** um estado só para a tela; todos os blocos abertos usam a mesma ordem, senão cabeçalhos repetidos divergem.
9. **Enum novo** `CliSessionSortColumn`, nenhum valor novo em enum existente.
10. **Reordenar é corte seco**, sem animação de item. Só a cor do indicador usa token de motion existente.

## Especificação

### A02 — `presentation/CliSessionSort.kt` (`commonMain`, puro)

```kotlin
enum class CliSessionSortColumn { TOKENS, CACHE, COST, ACTIVE_TIME, RESPONSES, THROUGHPUT }

data class CliSessionSort(val column: CliSessionSortColumn, val descending: Boolean = true)

fun nextCliSessionSort(current: CliSessionSort?, column: CliSessionSortColumn): CliSessionSort?
```

Mais uma função para `List<CliSessionSummary>` e outra para `List<CodexCliSessionSummary>`, ambas sobre um núcleo
genérico que recebe o seletor de valor (`Double?`), `lastTs` e `sessionId`. A razão de cache do Codex hoje é privada em
`CodexCliSessionsScreen.kt`: o teste de A02 afirma o valor, e a função sai de lá para ter um dono só.

### A03 — `presentation/ui/components/AppSortableColumnHeader.kt`

`AppSortableColumnHeaderLabel(label, sortDescending: Boolean?, onClick, modifier, sortStateDescription)`, stateless.
`sortDescending == null` é coluna inativa. Arquivo próprio: `AppStructure.kt` tem 688 linhas.

- Mesma tipografia e largura de `AppColumnHeaderLabel`; o indicador não muda a largura nem trunca o rótulo.
- Coluna ativa: rótulo em `onSurface`, seta `ArrowDownward`/`ArrowUpward` à direita.
- Inativa: rótulo em `onSurfaceVariant`, sem seta; no hover, seta fraca.
- Semântica: `Role.Button` e `stateDescription` com a direção, porque cor não informa sozinha.
- Sem cor de acento: acento é identidade de fonte.

Contrato em `docs/design-system/components/data/AppColumnHeader.prompt.md`, `.jsx` e `.d.ts`.

### A04 e A05 — adoção

`CliSessionColumnHeader` e `CodexSessionColumnHeader` recebem `sort` e `onSortChange`. Session, Project e Model seguem
passivos. Anthropic ordena Tokens, Cache, Cost, Active time e Throughput; Codex, Tokens, Cache, Responses e Throughput.
Textos novos em `CliSessionsLabels`, por `localized`. Cada adoção registra a tela em
`docs/planos/prototipo-visual-opencode.html` e `docs/presentation.md` no mesmo commit; A04 acrescenta a linha AM ao
histórico de `.claude/skills/usage-monitor-visual-options/SKILL.md`.

## Atividades

| ID | Atividade | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Função pura de ordem e ciclo do clique | `frontend-dev` |
| A03 | Primitiva `AppSortableColumnHeaderLabel` e contrato | `frontend-dev` |
| A04 | Adoção na lista Anthropic e no bloco do time | `frontend-dev` |
| A05 | Adoção na lista Codex | `frontend-dev` |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | Concluída | Sem harness de teste: documento. Validado contra o código pelas leituras listadas em "Fatos confirmados". |
| A02 | Concluída | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.CliSessionSortTest"`, 9/9 `java.lang.AssertionError` (ex.: `CliSessionSortTest.kt:65`, `:72`, `:111`), sem erro de compilação. O texto expected/actual não foi capturado: o log só traz arquivo e linha, e o XML foi sobrescrito pelo verde. Verde: mesmo comando, `tests="9" failures="0" errors="0"`. |
| A03 | Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.AppSortableColumnHeaderTest"`. Vermelho da posição da seta, antes da correção: `seta começa antes do fim do rótulo: arrow.left=122.0 text.right=128.0`. Truncamento e movimento só ficaram vermelhos por mutação (seta no fluxo; `tween` cru), porque os testes foram reescritos depois do código: `rótulo 'Tokens (with cache)' trunca em 136dp (sortDescending=true): maxIntrinsicWidth=128.0, largura=120` e `expected:<Color(0.9490196, 0.92941177, 0.92941177, 1.0, sRGB IEC61966-2.1)> but was:<Color(0.72156864, 0.69803923, 0.69803923, 1.0, sRGB IEC61966-2.1)>`. `isLineEllipsized` foi descartado: seguiu verde sob a mutação. Medido sem mutação: 128 de 136dp, 74 de 84dp, 67 de 76dp. Verde após restaurar (`cmp` igual): `tests="10" failures="0" errors="0"`. `.jsx` conferido só por leitura. |

## Problemas em aberto e riscos

- Ordem da lista muda a cada coleta enquanto a sessão ativa cresce: esperado com coluna ativa, e a seleção não muda.
- Host de janela não muda; só conteúdo. Nenhuma plataforma nova a medir (#342).
