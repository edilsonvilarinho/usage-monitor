# Emoji da conta como lua em órbita na HUD — issue #585

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-13,
branch `feat/issue-585-emoji-moon`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O usuário apontou no print da issue que os emojis da conta (⭐, 💼) estão pequenos demais na barra HUD. Na rodada de
protótipos AR (`prototipos/issue-585/`) escolheu a **AR3 · Lua em órbita**, e na pergunta de geometria escolheu a lua
de **18 dp**, que não muda o tamanho da barra.

## Fatos confirmados (2026-11-13, `8b7ae135`)

- Selo hoje: `HudRingStrip.kt:253-268`, `AccountEmojiGlyph` com `Alignment.TopEnd` e `offset(+4, −4)` dp.
- `HUD_EMOJI_BADGE_SIZE = 14.dp`, `HUD_EMOJI_BADGE_OVERSHOOT = 4.dp` (`HudNotchGeometry.kt:60-61`); glifo = 80% da
  caixa (`AccountEmojiGlyph.kt:33,51`), 11,2 dp hoje.
- Respiro: `HUD_NOTCH_PADDING_ACROSS = 8.dp`, `HUD_ITEM_GAP = 12.dp`, `HUD_RING_TEXT_GAP = 6.dp`
  (`HudNotchGeometry.kt:80-83`). `HudNotchGeometryTest.kt:325` exige excesso ≤ 8, ≤ 6 e ≤ 12/2, e `hudNotchSizes`
  igual com e sem emoji; `HudNotchTest.kt:517-528` mede o selo contra o anel e o notch.
- Anel 64 dp (`AppGargantuaTokens.kt:8`). Lua de raio R encostada por fora do anel a 45°: centro a 32 + R do centro
  do anel; excesso sobre a caixa do anel = (32 + R)/√2 + R − 32. R = 9 (18 dp) → 6,0 dp, o máximo que o respiro
  aceita. R = 11 (22 dp, o do protótipo) → 9,4 dp, não cabe.
- Emoji é só de conta Claude (`HudModel.kt:411`, `anthropicProfileId`).

## Decisões

- Lua de 18 dp: disco `AppGargantuaTokens.core` (`#030508`) com fio dourado de 1 dp; glifo a 80% (14,4 dp, +29%).
- Excesso 6 dp; `hudNotchSizes` não muda.
- Troca de emoji: a lua velha sai pela órbita e a nova entra (420 ms, função pura de quadro, durações em
  `AppGargantuaTokens` com espelho em `motion.css`); "reduzir animações" = corte seco.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Geometria da lua (constantes) + testes de que a lua não toca a trilha e não sai do notch |
| A03 | Lua desenhada (`AccountEmojiGlyph(moon = true)`: disco `core` + fio 1 dp `gold` por `drawBehind`); troca de emoji por função pura `accountMoonSwapFrame` (velha gira −20° em torno do anel e some em 210 ms, nova entra com alfa e escala 0,7→1 até 420 ms, sem overshoot), tokens `moonSwapOutMillis`/`moonSwapMillis` com espelho em `motion.css`; `appTweenSpec` corta seco com animação reduzida | Vermelho: `AccountMoonSwapTest` contra stub → `AssertionError` em `AccountMoonSwapTest.kt:21` (duração 420 ausente). Verde: `gradlew.bat desktopTest --tests "*AccountMoonSwapTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.architecture.*"` → 6 + 77 + 17 testes, 0 falhas. Desenho do disco sem teste de pixel (validado por compilação e captura na A04) |
| A03 | Desenho da lua (disco + fio) e troca animada |
| A04 | Docs: `compose-implementation.md` "Emoji por conta", `hud-notch.md`, protótipo `prototipo-visual-opencode.html`, histórico da skill `usage-monitor-visual-options` |

## Ponto de situação

| Id | O que foi feito | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos, decisão de 18 dp do usuário e atividades | `gh issue comment 585` → plano curto publicado; geometria conferida em `HudNotchGeometry.kt:60-83` |
| A02 | `HUD_EMOJI_BADGE_SIZE` 14 → 18 dp e `HUD_EMOJI_BADGE_OVERSHOOT` 4 → 6 dp, com a conta no comentário; teste novo da tangência e do excesso no respiro (a tangência já era verdade com 14/4: o centro do selo fica a 41 dp nos dois casos; o vermelho veio do excesso) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest"` → `AssertionError: excesso do selo 4.0 dp, alvo 5.991379 dp` (`HudNotchGeometryTest.kt:361`). Verde: mesmo comando + `--tests "com.usagemonitor.ui.HudNotchTest"` → 40 + 77 testes, 0 falhas |

## Problemas em aberto e riscos

- A troca animada só acontece quando o emoji muda nas Configurações com a HUD aberta; o caso comum é a lua já parada.
