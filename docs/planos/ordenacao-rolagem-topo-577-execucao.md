# Ordenação esconde o primeiro item sob o cabeçalho nas listas CLI (#577)

- **Modelo:** Claude Opus 5.5 (orquestração); implementação pelos agentes de `.claude/agents/`.
- **Ferramenta:** Claude Code.
- **Data:** 2026-11-12.
- **Branch:** `fix/issue-577-sort-scroll-top`.
- **Identidade de commit:** `claude <claude@anthropic.com>`, por comando.

## Objetivo

Depois de trocar a ordenação (coluna ou direção) de uma lista de sessões ou do Resumo, o primeiro item da nova ordem
aparece logo abaixo do cabeçalho. Hoje ele fica escondido acima da área visível e o usuário precisa rolar para cima.

## Fatos confirmados

| Fato | Como foi medido |
|---|---|
| Lista de sessões Anthropic: `LazyColumn` com `key = sessionId` e `rememberLazyListState()`; nada rola ao ordenar | `CliSessionsScreen.kt:163`, `:313-327` (leitura pelo `explorer`) |
| Lista Codex: `LazyColumn` com `key = sessionId`, sem `state` explícito | `CodexCliSessionsScreen.kt:314-324` |
| Time: um `LazyColumn` para a tela inteira; sessões de cada integrante com key `memberKey:sessionId` e cabeçalho no meio da lista | `TeamUsageScreen.kt:505-511`, `:645-663` |
| Resumo: troca de ordem/direção zera só `pageIndex`, não a rolagem | `CliUsageBreakdownPane.kt:159-165`, `:500-511` |
| Nenhum `scrollToItem`/`animateScrollToItem`/`LaunchedEffect` nesses arquivos | grep do `explorer` |
| O Compose mantém a rolagem ancorada na key do primeiro item visível quando a lista muda de ordem | documentação de `LazyListScope.items(key=)`; screenshots da issue (lista ordenada sem o 1º item visível) |

## Decisões

1. Ao mudar `sort` (coluna ou direção) a lista rola **sem animação** (`scrollToItem`) para o primeiro item da nova ordem.
   Corte seco, como a reordenação (#537, decisão 10).
2. Anthropic, Codex e Resumo: rola para o índice 0 da lista.
3. Time: a lista é a tela inteira e a ordenação é uma só para todos os blocos (#537, decisão 8); rolar ao topo tiraria o
   usuário do integrante que ele ordenou, e a âncora do Compose pode prender a rolagem numa sessão de **outro** bloco
   aberto acima. Ao mudar a ordenação, a lista rola até o cabeçalho de sessões **clicado** (lembrado pelo integrante),
   que fica no topo com a primeira sessão da nova ordem logo abaixo.
4. A primeira composição não rola: só a **mudança** de ordenação dispara.
5. Lógica de "para onde rolar" sai em função pura testável; a tela só chama.

## Atividades

| ID | O quê |
|---|---|
| A01 | Este plano. |
| A02 | Anthropic + Codex: teste de tela vermelho (rolar, ordenar, primeiro item visível) e correção. |
| A03 | Resumo (`CliUsageBreakdownPane`): mesmo teste e correção. |
| A04 | Time (`TeamUsageScreen`): teste e correção pela decisão 3; registro em `docs/presentation.md`. |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | Concluída | Sem harness de teste: documento. Validado pelas leituras de "Fatos confirmados". |
| A02 | Concluída | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CliSessionsScreenTest" --tests "com.usagemonitor.ui.CodexCliSessionsScreenTest"`: `CliSessionsScreenTest.kt:1718: Assert failed: The component with TestTag = 'cliSessionRow:sess39' is not displayed!` e `CodexCliSessionsScreenTest.kt:323: Expected exactly '1' node but could not find any node that satisfies: (Text … contains 'cx39' …)`. Verde: mesmo comando, 56 + 27 = 83 testes, 0 falhas. Helper `ScrollToTopOnSortChange` em `SortScrollReset.kt`; a lista Codex ganhou `LazyListState` explícito. |
| A03 | Concluída | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CliUsageBreakdownPaneTest"`: `java.lang.AssertionError: Assert failed: The component with Text + InputText + EditableText contains 'proj39' (ignoreCase: false) is not displayed!` (pré-condição de rolagem verde). Verde: `CliUsageBreakdownPaneTest` + `CliSessionsScreenTest` + `CodexCliSessionsScreenTest`, 9 + 56 + 27 = 92, 0 falhas. Chave de rolagem = `(sort, descending)`; o helper passou a aceitar `Any?`. `SortScrollResetTest` (commonTest, 3 casos) é de caracterização, nasce verde. Com cabeçalho de totais, o índice 0 é o bloco de totais. Troca de eixo fora do escopo. |

## Problemas em aberto e riscos

- Nenhum teste exercita o sistema de janelas (#342); a rolagem é coberta por `runDesktopComposeUiTest`, sem janela real.
