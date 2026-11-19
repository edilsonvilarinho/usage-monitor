# Balão do notch fecha ao minimizar/restaurar; clique no buraco força a coleta — issue #631

Procedência: Claude Opus 5.5 (orquestração), implementação por `frontend-dev`; Claude Code CLI no Windows 11,
2026-11-19, branch `fix/issue-631-hud-hole-balloon`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

1. Minimizar ou restaurar uma conta pelo balão do notch (botões "−"/"+" de `HudMinimizeButton`) deixa o balão
   aberto. No buraco, a captura do dono mostra o detalhe da conta já restaurada, com "← 2 no buraco" e o botão "−".
   O dono decidiu: o balão **fecha**.
2. Clicar no anel da barra força a coleta da conta (`HudNotch.kt:314`); no buraco negro, nenhum clique numa conta
   faz isso. O dono decidiu: o glifo em órbita **e** a linha da lista forçam a coleta; a linha continua abrindo o
   detalhe, e o anel central continua alternando a lista.

## Fatos confirmados (2026-11-19, `e2e41faf`)

- `HudWindow.kt:173-177`: `toggleMinimize` só grava a preferência; não toca `HudNotchBalloonState`.
- `HudNotch.kt:223-226`: único reflexo no balão — limpa `holeSelection` quando ela sai de `holeMembers` (a lista volta)
  e fecha só com o buraco vazio. Minimizar pelo balão do anel não fecha nada: `balloonIndex` continua no índice.
- `HudBlackHoleBalloonTest.kt:170` e `:182` afirmam hoje que a lista volta após Restaurar/saída por outro caminho;
  contrariam a decisão do dono e serão invertidos.
- `HudBlackHoleCell.kt:58`: o `clickable` da célula inteira (106 dp) chama `holeClicked`; os glifos
  (`HudBlackHoleOrbitGlyphs.kt:154`) não têm clique próprio. `HudNotch.kt:417`: a linha só grava `holeSelection`.
- `wc -l`: `HudNotch.kt` = 749 linhas (teto 800); função `HudNotch` = linhas 142-435 (293, teto 300).

## Decisões

- O balão fecha quando muda o estado de minimização **da conta que ele mostra** (anel ou detalhe do buraco), por
  qualquer caminho. Mudança em outra conta não fecha.
- Lógica nova mora em `HudNotchBalloonState.kt`; o efeito de `HudNotch.kt:223-226` sai da função, para ela
  continuar abaixo de 300 linhas.
- Hipótese não confirmada: o app real diverge do teste de tela atual (o teste vê a lista voltar, a captura vê o
  detalhe). A correção não depende da causa; a validação final é no app real.

## Atividades

- A01: este plano.
- A02: balão fecha quando a conta mostrada muda de minimização (TDD em `HudBlackHoleBalloonTest`).
- A03: clique no glifo em órbita força a coleta daquela conta; anel central segue alternando a lista.
- A04: clique/Enter numa linha da lista abre o detalhe e força a coleta.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `git worktree add .claude/worktrees/hud-hole-631 -b fix/issue-631-hud-hole-balloon origin/main` | plano escrito; sem harness de teste |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest"` | 19 testes, 3 falhas na asserção: restaurar/outro caminho "Did not expect any node but found '1' node … 'Anthropic — Alfa'"; anel "Did not expect any node … (ContentDescription = 'Restaurar')"; "outra conta não fecha" já verde |
| A02 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "*ArchitectureRulesTest*"` | BUILD SUCCESSFUL: 19 + 99 + 28 testes, 0 falhas; `HudNotch.kt` 749 → 746 linhas |
| A03 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest"` (glifo sem `clickable`) | 21 testes, 1 falha na asserção: `clique num glifo do buraco chama refresh da conta e mantem a lista aberta` → `AssertionError` em `HudBlackHoleBalloonTest.kt:266` (refresh vazio) |
| A03 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest" --tests "com.usagemonitor.ui.HudBlackHoleCellTest" --tests "*ArchitectureRulesTest*"` | BUILD SUCCESSFUL: 21 + 13 + 28 testes, 0 falhas (JUnit XML); 1ª entrega devolvida (clique da célula reescrito à mão, asserção contra o hover da #612) |
| A04 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "*ArchitectureRulesTest*"` | 151 testes, 3 falhas na asserção (`AssertionError`): clique na linha (`:266`), Enter na linha (`:283`), voltar não recoleta (`:300`) |
| A04 | verde: mesmo comando | BUILD SUCCESSFUL: 24 + 99 + 28 testes, 0 falhas (JUnit XML) |

## Problemas em aberto e riscos

- Mudança de host de janela não há; só o conteúdo do notch. A suíte roda só no Windows (#342).
