# Reordenar as contas arrastando o anel na HUD — issue #584

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-13,
branch `feat/issue-584-hud-reorder`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O usuário quer trocar a posição das APIs/contas na barra HUD. Na rodada de protótipos AQ (`prototipos/issue-584/`)
escolheu a **AQ1 · Captura orbital por arrasto**: segurar o anel 300 ms, arrastar ao longo da barra, soltar.

## Fatos confirmados (2026-11-13, `8b7ae135`)

- A ordem do notch é a `cardOrder` (`HudModel.kt:343-362`, `CardLayoutPreferences.kt:216-228`), persistida em
  `CARD_ORDER_KEY = "cardOrder"` (`AppPreferenceKeys.kt:40`, leitura/escrita `:66-83`). Leitores: `HudWindow.kt:187`,
  `Main.kt:435`, `SettingsWindowHost.kt:189`, `AppTrayHost.kt:52`.
- Única escrita hoje: `AppShellState.keepCardsOf` (`AppShellState.kt:148-151`, chamada em
  `AppPreferenceEffects.kt:37`), que só poda e acrescenta. `cardOrder` tem `private set` (`:79-82`). Não há escrita de
  ordem arbitrária; `moveVisibleCardToIndex`/`reorderVisibleCards` (`CardLayoutPreferences.kt:136,162`) só devolvem
  lista e só têm chamada em teste.
- Gesto do notch: `HudNotch.kt:265-278` usa `hudPressGesture(draggable = false)` e o clique recoleta a conta por
  `ringItemBounds.indexAt`. `HudPressGesture.kt:27-96` já tem `onDragStart/Move/End` com `touchSlop`. Hover abre o
  balão (`HudNotch.kt:292`).
- Região de clique (`hudRestHitRegion`, `HudNotchGeometry.kt:629-655`) cobre o notch inteiro: o arrasto dentro do
  notch recebe eventos em todas as plataformas.
- Testes de ordem: `HudModelTest.kt:49,350,534`, `HudObservedModelTest.kt:82`, `CardLayoutPreferencesTest.kt:22-120`.

## Decisões

- Pressão longa de 300 ms distingue o arrasto do clique (recoleta); arrasto ao longo do eixo da barra (vertical nas
  bordas laterais, horizontal em cima e embaixo). Esc cancela e devolve.
- Vizinhas deslizam com mola sem overshoot (320 ms); soltar assenta em 240 ms; durações em `AppGargantuaTokens` com
  espelho em `motion.css`; "reduzir animações" = troca seca.
- A janela e o notch não mudam de tamanho durante o arrasto: só o desenho se desloca.
- Uma escrita de ordem só (`AppShellState`), que a bandeja e as Configurações já leem.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Regra pura do arrasto em `commonMain` (índice de destino, deslocamento das vizinhas, nova ordem) + testes |
| A03 | Escrita da ordem em `AppShellState` (`CARD_ORDER_KEY`) + teste |
| A04 | Gesto na HUD (pressão longa, arrasto, Esc) e halo de captura; teste de componente |
| A05 | Docs: `hud-notch.md`, `presentation.md` ("Perdas aceitas"), protótipo, histórico da skill `usage-monitor-visual-options` |

## Ponto de situação

| Id | O que foi feito | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos e atividades | `gh issue comment 584` → plano curto publicado; fatos levantados por varredura de leitura |

## Problemas em aberto e riscos

- Host de janela (#342): o gesto só foi exercitado no Windows; o PR declara o risco no Linux/macOS.
- Pressão longa no notch compete com o arrasto da alça de mover (`HudHandles.kt:100`): a alça fica fora do anel.
