# Modo flutuante no Linux e balão da engrenagem ancorado — issue #623

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-19,
branch `fix/issue-623-floating-linux-gear` (worktree `.claude/worktrees/hud-float-623`, base `origin/main` `1c629514`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido do dono (#623, com capturas): (1) no Linux o botão que ativa o modo flutuante não aparece; (2) no modo
flutuante o balão da engrenagem abre embaixo, longe dela, e precisa abrir ao lado do botão; (3) testes de posição para
conter o refluxo visual.

## Fatos confirmados (2026-11-19, `1c629514`)

Varredura do agente `explorer` e leitura direta.

- `hudFloatingSupported` (`HudFloatingPlacement.kt:56-57`) devolve `platform == WINDOWS`; alimentado em
  `HudWindowContent.kt:183`. No notch o botão só compõe com `floatingSupported` (`HudNotch.kt:399`).
- `focusedRingIndex(HudFloatingBalloon.App)` devolve `null` (`HudFloatingHud.kt:247-251`), e `focusedRingCenterY`
  cai no fallback `maxHeight / 2f` (`HudFloatingHud.kt:170-175`): o balão da engrenagem fica no centro vertical da
  janela, e a engrenagem fica acima ou abaixo da coluna (`buttonsRect`, `HudFloatingLayout.kt`).
- Nenhum teste de posição vertical do balão da engrenagem: `HudFloatingHudTest.kt:178-196` só confere conteúdo e altura;
  `HudFloatingLayoutTest.kt:327-350` cobre `hudFloatingBalloonTop` só para anel.
- `docs/hud-notch.md`, tabela "Fora do alcance dos testes": no Linux o recorte só vale sem alfa (#426); clique em pixel
  transparente engolido é "provável no X11", custo não medido.

## Decisões

- **Linux liberado por decisão do dono (2026-11-19)**, com medição no próximo beta. macOS continua fora (#340).
  Risco declarado no PR (#342): com alfa, a área transparente da janela flutuante pode capturar cliques do desktop.
- Balão da engrenagem centrado no centro Y da engrenagem (a primeira alça da pilha), pela mesma `hudFloatingBalloonTop`.

## Atividades

| ID | Atividade |
|---|---|
| A01 | Este plano |
| A02 | `hudFloatingSupported` aceita `LINUX`; teste de plataforma primeiro; `docs/hud-notch.md` atualizado |
| A03 | Âncora do balão da engrenagem no centro Y da engrenagem; testes de posição (pura e de tela, pilha em cima e embaixo) |
| A04 | Engrenagem abre o balão no hover no modo flutuante, como no notch (`HudNotch.kt:227-232`); achado pelo dono no teste local |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | feito | plano criado; sem harness de teste (documento) |
| A02 | feito | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudFloatingPlacementTest"` → `floating mode is supported on Windows and Linux only FAILED`, `AssertionError at HudFloatingPlacementTest.kt:97` (`assertTrue(LINUX)`); verde: mesmo comando, 9 testes, 0 falhas |
| A03 | feito | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudFloatingLayoutTest" --tests "com.usagemonitor.ui.HudFloatingHudTest"` → `HudFloatingLayoutTest.kt:211 expected:<578.0.dp> but was:<562.0.dp>`, `:220 expected:<412.0.dp> but was:<396.0.dp>`, `HudFloatingHudTest.kt:204 centro da engrenagem y=1295.0 fora do balão 814.0..934.0`, `:212 y=36.0 fora do balão 774.0..894.0`; verde: mesmo comando + `*ArchitectureRulesTest*`, 83 testes, 0 falhas (34 + 21 + 28) |

## Problemas em aberto e riscos

- Linux com alfa: recorte não aplicado (#426); bloqueio de cliques pela área transparente não medido. Medir no beta.
