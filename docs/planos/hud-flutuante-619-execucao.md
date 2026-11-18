# HUD flutuante em qualquer ponto da tela — issue #619

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-18,
branch `feat/issue-619-floating-hud` (worktree `.claude/worktrees/hud-flutuante`, base `origin/main` `36590e6d`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#619, com capturas): um modo em que a HUD não é obrigada a encostar numa borda — fica onde for largada, em
qualquer ponto da tela — e tem uma forma minimizada que não atrapalha o uso do computador. Restrição do dono: dentro do
design system e do tema buraco negro (Gargantua); a referência da forma minimizada é o anel com disco inclinado.

## Fatos confirmados (2026-11-18, `36590e6d`)

Varredura do agente `explorer` sobre `docs/hud-notch.md` e `Hud*.kt`.

- Bordas `TOP/BOTTOM/LEFT/RIGHT` em `HudNotchGeometry.kt:40`. Ao soltar o arrasto, `HudWindow.kt:291-301` chama
  `hudDragLanding` (`HudWindowDrag.kt:64`), que usa `nearestHudPlacement` (`HudNotchGeometry.kt:716-735`): borda mais
  próxima do centro. Posição livre só existe durante o arrasto. `HudPlacement(edge, offsetFraction)` em `:700`.
- Preferências `hudEdge`/`hudEdgeOffset` (`HudWindowPreferences.kt:73-74`), lidas em `readPersistedHudPlacement` (`:90`)
  e gravadas em `persistHudPlacement` (`:114-120`). `hudAutoRetract` (`:122-132`), padrão `false`.
- Botões (`HudHandles.kt`): `HudMoveHandle` (`:84`), `HudRetractHandle` (`:135`), `HudGearHandle` (`:183`).
- Faixa recolhida: `HudRetractedStrip.kt:80`, espessura 10 dp (`HudNotchGeometry.kt:267`), glifos `:174-182`
  (triângulo crítico, losango atenção, disco em dia, anel vazio sem projeção); abre por hover com intenção de 200 ms
  (`HudRetract.kt:21-31`).
- `AppHudBar.prompt.md`, seção Gestures (`:187-191`), ainda cita o clique direito "to Cards", que o `hud-notch.md` diz
  ter sido removido: divergência a corrigir quando a tela mudar.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-619/`, fora do versionamento), 10 direções pedidas
  pelo dono. Nenhum Kotlin antes da escolha.
- O modo encaixado de hoje não muda; o flutuante é adicional.
- Comportamento nativo novo (janela solta, transparência, clique atravessável) nasce restrito à plataforma medida (#340).

## Atividades

| ID | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-flutuante-619-execucao.md` |
| A02 | 10 protótipos com direções distintas; escolha do dono | `prototipos/issue-619/` (fora do versionamento) |
| A03+ | Implementação TDD da direção escolhida (definida após a escolha) | a definir |

## Ponto de situação

| ID | Estado | Execução |
|---|---|---|
| A01 | feito | sem harness (documento); conferido por leitura |
| A02 | em andamento | — |

## Problemas em aberto e riscos

- Janela solta fora da borda muda a premissa da `hud-notch.md` (forma de encaixe, `hudHitRegionMode`, alfa no Linux);
  o custo depende da direção escolhida.
- Clique atravessável e transparência por proximidade (se escolhidos) são nativos por plataforma: medir antes de liberar.
