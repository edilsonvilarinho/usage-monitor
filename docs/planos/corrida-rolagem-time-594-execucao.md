# Corrida da rolagem por roda no teste da tela de uso do time — issue #594

Procedência: Claude Opus 5.5 (orquestração) com agente de implementação, Claude Code CLI no Windows 11, 2026-11-14,
branch `fix/issue-594-team-scroll-race`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

`TeamUsageScreenTest > clicking a member's session header after scrolling brings that header to the top` falha no CI
com `IllegalArgumentException: GraphicsContext is already closed` e barrou a beta `v42.3.0-beta.5` (tag sem release).
Tornar o teste determinístico sem mudar o que ele afirma (#577: ordenar o bloco leva a lista ao cabeçalho dele).

## Fatos confirmados

- Falhou 3/3 no commit `4a1700fc`: run 43613701668 (`ci.yml`, push na `main`) e run 43618400495 (`Release Desktop
  Packages`, execução original e `gh run rerun --failed`), sempre com `-PtestForks=3`
  (`gh run view <id> --log-failed | grep FAILED`). O PR #593 (run 43613013861, mesma árvore) passou; as 7 runs
  anteriores da `main` passaram.
- Local não reproduz: `desktopTest --tests "com.usagemonitor.ui.TeamUsageScreenTest" --rerun` 3× verde (61/61) e
  `desktopTest -PtestForks=3 --rerun --no-build-cache` verde (4374, 0 falhas).
- Pilha do CI: `SkiaGraphicsContext.createGraphicsLayer` ← `LazyLayoutItemAnimation.<init>` ← `LazyListState.onScroll`
  ← `ScrollingLogic.scrollBy` ← `MouseWheelScrollingLogic.dispatchMouseWheelScroll` (relatório HTML do artefato
  `test-reports` do run 43618400495).
- Fonte do Compose 1.12.2 (`foundation-desktop-1.12.2-sources.jar`, Maven Central,
  `commonMain/androidx/compose/foundation/gestures/MouseWheelScrollingLogic.kt`): a roda é enfileirada num `Channel`
  e consumida por corrotina própria (`startReceivingEvents`, linha 103); depois de cada delta ela espera o próximo com
  `withTimeoutOrNull(ScrollProgressTimeout = 50 ms)` (linhas 199-201, 228, 320). Desligar a rolagem suave
  (`compose.scrolling.smooth.enabled=false`, lido em `DesktopScrollConfig`) só muda `shouldApplyImmediately`
  (linha 132): o canal e a corrotina continuam.
- As linhas das sessões têm chave `"${member.memberKey}:${sessionId}"` e animação de item (`appItemMotion()`)
  (`TeamUsageScreen.kt:644-671`).

## Decisões

- Posicionar a lista por `performScrollToKey` (semântica `ScrollToIndex` → `LazyListState.scrollToItem`, sem corrotina
  de roda nem timeout em tempo real), mantendo a asserção `rowAtTopEdge` como precondição explícita.
- Não desligar a rolagem suave por propriedade de sistema: não remove a corrotina (fato acima).

## Atividades

- A01 — este plano.
- A02 — teste sem roda do mouse em `TeamUsageScreenTest.kt`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (documento) | plano revisado contra os runs e a fonte citados |

## Problemas em aberto e riscos

- Sem reprodução local da corrida (medido acima): o vermelho do TDD não é reproduzível localmente; a validação é o CI
  com `-PtestForks=3` do PR e o `verify` da release da `v42.3.0-beta.6`.
- `CliSessionsScreenTest.kt:1714`, `CliUsageBreakdownPaneTest.kt:161` e `CodexCliSessionsScreenTest.kt:319` também
  rolam por `repeat(200) { scroll(1f) }`; nenhuma falha registrada. Ficam fora desta issue.
