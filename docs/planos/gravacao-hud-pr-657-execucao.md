# Gravar o app real em todo PR de HUD — issue #657

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `ci/issue-657-hud-recording` (worktree `.claude/worktrees/hud-recording-657`, base `origin/main` `889aca1f`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644; continua a #652.

## Objetivo

PR que mexe na HUD ou em janela grava o app sozinho, em mais cenários que "parado no modo flutuante". Informativo.

## Fatos confirmados (2026-11-24, `889aca1f`)

- #652: `real-app.yml` manual; 15/15 vereditos certos; 121–264 s por job que compila, 65–88 s por tag; ~9,4 quadros/s.
- `record.ps1` grava um cenário por execução; `seed.ps1` fixa `hudFloating` e `hudAutoRetract`.
- Chaves de posição: `hudEdge`, `hudEdgeOffset`, `hudMinimizedAccounts`, `hudFloatX/Y` (`HudWindowPreferences.kt`).
- Um workflow próprio já fica fora do `ci-ok` sem tocar no `ci.yml`.

## Decisões

- **Gatilho `pull_request` com `paths` no próprio `real-app.yml`:** `src/desktopMain/**/Hud*.kt`, `**/*Window*.kt`,
  `**/*Frame*.kt`, `**/presentation/ui/Hud*.kt`, `src/commonMain/**/AppGargantua*.kt`, `**/Hud*.kt` do `commonMain`,
  `tools/real-app/**`, `tools/ci/hud-pulses.mjs` e o próprio workflow. Caso `source`, 1 tentativa.
- **Uma compilação, vários cenários** no mesmo job, em sequência, cada um com pasta de dados e saída próprias.
- **Cada cenário tem a invariante escrita antes:**

| Cenário | Entrada | Invariante |
|---|---|---|
| `floating-idle` | ponteiro parado | nenhum pulso |
| `docked-<borda>-idle` (4) | ponteiro parado, auto-recolher ligado | nenhum pulso |
| `docked-top-hover` | ponteiro entra no notch e sai | exatamente 1 pulso, que abre até 1,5 s depois de entrar e fecha até 2,5 s depois de sair |

- Arrasto e buraco negro ficam para a A04: dependem de achar o alvo na tela, e o hover prova primeiro se a entrada
  sintética chega ao app.
- **Prova por cenário:** um defeito conhecido ou plantado reprovado, e a `main` aprovada, 5 vezes cada.

## Atividades

- **A01** — este plano.
- **A02** — detector: modo `--expect hover`, em TDD.
- **A03** — cenários em `seed.ps1`/`record.ps1`, laço no workflow e gatilho de PR; 5 rodadas da `main`.
- **A04** — arrasto e buraco negro.
- **A05** — prova com defeito plantado por cenário; critério para bloquear.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | conferido contra `real-app.yml` e `HudWindowPreferences.kt` |

## Problemas em aberto e riscos

- Entrada sintética (`SetCursorPos`/`SendInput`) pode não gerar o evento que o Compose espera; a A03 responde.
- O repouso do encaixado com auto-recolher pode ter menos que os 400 px do detector: `not-visible` seria o resultado.
