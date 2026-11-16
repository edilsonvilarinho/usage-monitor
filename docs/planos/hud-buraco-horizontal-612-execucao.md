# Buraco negro das minimizadas também na borda horizontal — issue #612

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-16,
branch `feat/issue-612-horizontal-black-hole` (worktree `.claude/worktrees/issue-612`, base `origin/main` `cc37bd08`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#612, com capturas): "o mesmo comportamento vertical quando estiver minimizado tem que aplicar no horizontal".
Na borda de cima e de baixo, as contas minimizadas passam a ir para o buraco negro (#600, #602, #611), como na lateral.
Segundo pedido: "não precisa desse texto indicando quantidades de APIs minimizadas". O rótulo "N minimizadas" sai da
célula do buraco nas duas bordas.

## Fatos confirmados (2026-11-16, `cc37bd08`)

Todos lidos nos arquivos citados (varredura do agente `explorer`, conferida por `Grep`).

- O buraco é desligado na horizontal por retorno antecipado: `HudBlackHole.kt:53-55` (`if (edge.isHorizontal)` →
  `holeMembers = emptyList()`). Quem chama: `HudNotchGeometry.kt:205`, `:442` (`verticalCollapsed` fixa `HudEdge.LEFT`),
  `HudNotch.kt:207`, `HudRingStrip.kt:117`.
- Faixa horizontal: `horizontalCollapsed` (`HudNotchGeometry.kt:401-434`). Altura = anel de 64 dp + linha de foco, mais
  8 dp de padding de cada lado. A conta minimizada mede `minimizedRingWithFocusWidth` (`HudNotchMinimized.kt:14-15`):
  anel de 28 dp + 6 dp + a cota em foco. Vão de 12 dp entre itens.
- A célula do buraco (`HUD_BLACK_HOLE_CELL_SIZE` = 236 dp, `HudBlackHoleGeometry.kt:23`) só entra na conta da vertical
  (`HudNotchGeometry.kt:464`, `:482`).
- Desenho: a horizontal desenha todos os índices como `HudRingItem` (`HudRingStrip.kt:137`, `:193-200`). A vertical
  põe `HudBlackHoleCell` depois da última conta (`:172-186`) e o voo em `HudBlackHoleFlights` (`:220`).
- Rótulo de contagem: `hudBlackHoleCountLabel` (`HudBlackHoleLabels.kt:6`), desenhado em `HudBlackHoleCell.kt:66-67`,
  também usado na semântica (`:49`, `:60`) e no cabeçalho da lista (`HudBlackHoleList.kt:47`). A largura do rótulo entra
  no piso da coluna vertical (`HudNotchGeometry.kt:467-474`).
- Auto-minimizar é só vertical: `HudAutoMinimize.kt:24`.
- Testes que fixam "horizontal inalterada": `HudBlackHoleTest.kt:75-81`, `HudBlackHoleCellTest.kt:56-61`,
  `HudNotchTest.kt:343-351` e `HudNotchGeometryTest.kt:619-624`. Esses testes mudam de sentido nesta issue.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-612/`, fora do versionamento). Nenhum Kotlin
  antes da escolha do usuário.
- O rótulo sai do desenho, mas a contagem continua na descrição semântica da célula: sem texto, o leitor de tela perde a
  quantidade. O cabeçalho da lista não muda, porque não foi pedido.
- Auto-minimizar segue só na vertical: o pedido é sobre as minimizadas, e a regra de 45% da horizontal não muda.

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-buraco-horizontal-612-execucao.md` |
| A02 | Protótipos com direções distintas; escolha do usuário | `prototipos/issue-612/` (fora do versionamento) |
| A03 | TDD: `hudColumnArrangement` deixa de restringir o buraco à vertical | `HudBlackHole.kt` |
| A04 | TDD: geometria e desenho da faixa horizontal com o buraco, na direção escolhida | `HudNotchGeometry.kt`, `HudRingStrip.kt` |
| A05 | TDD: o rótulo de contagem sai do desenho (contagem segue na semântica) | `HudBlackHoleCell.kt`, `HudNotchGeometry.kt` |
| A06 | `docs/hud-notch.md`, `AppHudBar.prompt.md` e protótipo de tela | docs |

## Ponto de situação

| Id | Situação | Comando e resultado |
|---|---|---|
| A01 | feito | sem harness de teste (documento); validado pela leitura dos arquivos citados em "Fatos confirmados" |

## Problemas em aberto e riscos

- A faixa horizontal tem cerca de 96 dp de altura e a célula do buraco tem 236 dp. A direção escolhida define se a faixa
  cresce, se a órbita se achata ou se a célula pende da faixa.
- Mudança no tamanho da janela da HUD horizontal: o risco de sistema de janelas da #342 vale (suíte só no Windows).
