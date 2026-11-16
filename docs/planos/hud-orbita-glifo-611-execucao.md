# Glifo em órbita do buraco negro legível — issue #611

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-16,
branch `fix/issue-611-orbit-glyph-size` (worktree `.claude/worktrees/issue-611-orbit-glyph-size`, base `origin/main`
`56559f2f`), commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#611, com duas capturas): os ícones das APIs em órbita do buraco negro "ficaram pequenos demais", sobretudo
quando a conta está em execução e o glifo carrega os arcos de cota. A marca do provedor precisa ser distinguível nos
dois estados (ocioso e em execução), sem perder a informação de cota e de execução que a #602 fixou.

## Fatos confirmados (2026-11-16, `56559f2f`)

- Glifo em órbita: `HUD_BLACK_HOLE_GLYPH_SIZE = 24.dp`, raio `HUD_BLACK_HOLE_ORBIT_RADIUS = 45.dp`, passo
  `min(40°, 360°/n)` (`HudBlackHoleOrbit.kt:10`, `:13`, `:19`, `:49`). Leitura do arquivo.
- Célula: `HUD_BLACK_HOLE_CELL_SIZE = (45 + 24/2) × 2 = 114 dp` (`HudBlackHoleGeometry.kt:23`); anel central de 64 dp
  (`AppGargantuaTokens.size`, `HudBlackHoleCell.kt:58-61`). Leitura do arquivo.
- O glifo é um `AppGargantuaRing` inteiro em miniatura (cena Gargantua + arcos de cota) com traço
  `2,5 × 24/64 ≈ 0,94 dp` e `AppProviderMark` no centro com o tamanho padrão de 16 dp
  (`HudBlackHoleOrbitGlyphs.kt:131-137`, `AppProviderMark.kt:61`). Leitura do arquivo.
- Em execução o glifo ainda recebe halo radial de 18 dp, anel de 1 dp em 14 dp, rastro de 1,6 dp e o filamento de maré
  (`HudBlackHoleOrbitGlyphs.kt:56-63`, `:153-163`). Leitura do arquivo.
- Folga geométrica de hoje: borda interna do glifo em 45 − 12 = 33 dp, 1 dp fora do anel central (raio 32); corda entre
  vizinhos a 40° no raio 45 = 2·45·sen 20° ≈ 30,8 dp. Aumentar só o glifo no mesmo raio invade o anel central.
  Cálculo a partir das constantes acima.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-611/`); nenhum Kotlin antes da escolha do usuário.
- A geometria escolhida entra como função/constante pura com teste (TDD), como a da #602.

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-orbita-glifo-611-execucao.md` |
| A02 | Protótipos com direções distintas; escolha do usuário | `prototipos/issue-611/` (fora do versionamento) |
| A03 | TDD da geometria escolhida (glifo, raio, célula, sem sobreposição) | `HudBlackHoleOrbit.kt`, `HudBlackHoleGeometry.kt` |
| A04 | Desenho do glifo escolhido + `docs/hud-notch.md`, protótipo e design system | `HudBlackHoleOrbitGlyphs.kt`, docs |

## Ponto de situação

| Id | Situação | Comando e resultado |
|---|---|---|
| A01 | feito | sem harness de teste (documento); validado pela leitura dos arquivos citados em "Fatos confirmados" |

## Problemas em aberto e riscos

- Célula maior que 114 dp alarga a coluna vertical e muda a conta do auto-minimizar (`HudAutoMinimizeTest`, #600).
- Hipótese não confirmada: os glifos "▯" da captura podem ser a marca deformada pela cena Gargantua em 24 dp; só a
  captura real com a fonte identificada confirma.
