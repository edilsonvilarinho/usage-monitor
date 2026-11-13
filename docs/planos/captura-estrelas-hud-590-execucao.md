# Estrelas lentas capturadas pelos anéis e leitura reforçada no claro — issue #590

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-13,
branch `feat/issue-590-hud-star-capture`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

#590 pediu um fundo de espaço profundo na barra HUD e leitura melhor no tema claro ("muito claro"). Rodadas:
AU (15 opções, `prototipos/issue-590/`) → AU2 · Catálogo Messier com ressalvas (muito rápido; manter o reforço da
fonte no claro); AV (10 opções, `prototipos/issue-590-r2/`, supernovas e galáxias nascendo) sem escolha; AW (2 opções,
`prototipos/issue-590-r3/`, "só estrelas, não passando muito rápido, algumas capturadas pelas APIs") → **AW1**;
AX (`prototipos/issue-590-r4/ax-cores-ceu/`, cor do céu no claro) → recusada: "deixa branco mesmo como tá no original".

Resultado a entregar: o campo da #586 vira o da AW1 (três profundidades lentas, laço de 240 s, cintilação, brilho em
cruz), com **capturas** — a cada 8 s uma estrela espirala para dentro de um anel e some com um lampejo; no claro o
fundo continua a superfície branca de hoje com estrelas em tinta, e o texto ganha a **leitura reforçada**.

## Fatos confirmados (2026-11-13, `806619cd`, varredura do `explorer`)

- Quadro puro: `hudStarfieldFrame(timeMs, width, height, wells, forbidden, seed = 586)` (`HudStarfield.kt:150-162`),
  período 82 500 ms (:9), 140 estrelas 30/45/65 (:67-71), deriva `y0 + speed*height*phase` (:172) — velocidade em
  alturas por laço (perto 4, meio 2, longe 1): com corpo de ~600 dp, perto ≈ 29 dp/s. É o "muito rápido".
- Deflexão `hudStarfieldDeflect` (:221-242), sombra 0,62 R, influência 3 R. Tinta clara `hudStarfieldInk`
  (:271-275): `#171414`, alfa .95/.7/.45, raio ×1,5. Escuro: `AppGargantuaTokens.hot`/`gold`.
- Desenho: `HudStarfieldCanvas.kt` (98 linhas), gate `policy.continuous && !policy.reduced` (:46), senão `timeMs = 0`
  (:49-50); chamado em `HudNotch.kt:283` com `forbidden = hudStarfieldForbidden(boxes, rings)` (caixa do item menos o anel).
- Testes: `HudStarfieldTest.kt` (laço :55, nada em poço/caixa :63/:77, alfa zero em caixa :95).
- Rótulos (`HudRingStrip.kt`, 552 linhas): prefixo `onSurfaceVariant` (:523), valor `onSurface` (:521), estilo
  `labelSmall` Medium 10 sp ou `labelMedium` Normal 12 sp (:532; pesos em `AppTheme.kt:496-505`); atenção =
  `account.tone.color()` (:381). Pílula: `AppStatusPill` (:396), `STATUS_PILL_TINT_ALPHA = 0.08f`
  (`AppStatusPill.kt:40`) — **0,14 reprova AA no verde claro (4,17:1, `AppStatusPillContrastTest`)**.
- Parede do vidro: `GargantuaScene.light` `wallAlpha = 0.10f` (`GargantuaScene.kt:65`). Arco tracejado:
  `AppUsageRing.kt:206`.
- Protótipo escolhido (AW1, `prototipos/issue-590-r3/aw1-ceu-noturno-claro/index.html:179-414`): camadas perto
  3 dp/s · 60 · r 1,2–1,8 · α .8–.95; meio 1,5 · 56 · r .8–1,2 · α .6–.7; longe .5 · 40 · r .5–.8 · α .35–.5; período
  de cada camada `tile = v·240 s`, estrela repetida em cada cópia do tile dentro do corpo; cintilação 3/4/5/6 s
  (divide 240 s) ±15%; brilho em cruz nas 6 maiores de perto. Captura: a cada 8 s a partir de 4 s (30 por laço),
  anel `n mod poços`, ângulo `n·2,399963`; `r(s) = 2,4R(1−s)^1,6 + 0,55R·s`, `θ = θ0 + 3π·s`, `s = idade/2600 ms`;
  fade `ss(0,.12,s)·(1−ss(.9,1,s))`; rastro dos últimos 10 dp, alfa .9→0 e largura 1,6→.4; lampejo de 200 ms no
  ponto final, alfa .6→0, raio 5.

## Decisões

- O campo continua função pura de quadro (`commonMain`, sem Compose), determinística por semente; troca só a
  parametrização (camadas e velocidade absoluta em dp/s, laço 240 000 ms) e ganha cintilação e marca de brilho em cruz.
- A captura é outra função pura (`HudStarCapture.kt`), do tempo e dos poços: cabeça, rastro e lampejo; ponto dentro de
  caixa proibida tem alfa zero, como as estrelas. Desenhada no mesmo canvas, antes dos anéis (passa atrás deles).
- Reduzir animações: campo parado em t = 0 e **nenhuma** captura (a primeira começa em 4 s).
- Claro: superfície de hoje, tinta `#171414` com os alfas e o ×1,5 de hoje; captura em tinta; lampejo em tinta com
  alfa ≤ .35 (branco sumiria no branco). Sem céu pintado (recusado na AX).
- Leitura reforçada, só no claro e só na HUD: prefixo `onSurface` alfa .78 peso Medium; valor SemiBold; valor em
  atenção SemiBold; pílula com contorno de 1 dp do tom a 35% **mantendo o fundo 0,08** (o 0,14 da rodada AU reprova
  AA, fato acima); parede do vidro .22; arco tracejado ×1,6. Estrelas a ≤ 40% do alfa a até 8 dp de uma caixa proibida.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Campo lento da AW1 (camadas, laço 240 s, cintilação, brilho em cruz, atenuação perto do texto no claro) + testes |
| A03 | Capturas (função pura + desenho no canvas, gate de movimento) + testes |
| A04 | Leitura reforçada no claro da HUD (rótulos, pílula, parede, tracejado) + testes |
| A05 | Docs: `hud-notch.md`, design system, protótipo, histórico da skill `usage-monitor-visual-options` (AU, AV, AW, AX) |

## Ponto de situação

| Id | O que foi feito | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos, escolhas do usuário e decisões | `gh issue comment 590` → plano curto publicado |
| A02 | `HudStarfield.kt`: laço 240 000 ms, camadas 60/56/40 a 3/1,5/0,5 por segundo com `tile = v·240 s` e cópias dentro do corpo, cintilação ±15% (3–6 s), `glint` nas 6 maiores de perto (cruz no canvas), `hudStarfieldTextAttenuation` ×0,4 a ≤ 8 dp de caixa só no claro, `hudStarfieldDrawAlpha` usado pelo canvas | Vermelho: 11 de 19 na asserção (`HudStarfieldTest.kt:53`, `:62`, `:69`, `:80`, `:87`, `:131`, `:150`, `:187`, `:204`, `:213`, `:230`, `AssertionError`). Verde: `./gradlew.bat desktopTest --tests '*HudStarfieldTest' --tests 'com.usagemonitor.ui.HudNotchTest' --tests 'com.usagemonitor.architecture.*'` → 19 + 85 + 17, 0 falhas |
| A03 | `HudStarCapture.kt` (`hudStarCaptures`): agenda 4 000 + n·8 000 ms (30 por laço), poço `n mod poços`, ângulo `n·2,399963`, espiral `2,4R(1−s)^1,6 + 0,55R·s` em 2 600 ms, rastro de 10 dp (alfa .9→0, largura 1,6→.4), lampejo de 200 ms; alfa 0 em caixa proibida. Canvas desenha depois das estrelas e antes dos anéis, só com `continuous && !reduced`; lampejo ≤ .6 escuro, ≤ .35 claro (tinta). Smoothstep do campo passou a `internal` e é compartilhado | Vermelho: `uma cabeça em voo em t=5300 expected:<1> but was:<0>`, `um lampejo em t=6600 expected:<1> but was:<0>`, `HudNotchTest`: `capturas com movimento reduzido expected:<0> but was:<-1>`. Verde: `./gradlew.bat desktopTest --tests '*HudStarCaptureTest' --tests '*HudStarfieldTest' --tests 'com.usagemonitor.ui.HudNotchTest' --tests 'com.usagemonitor.architecture.*'` → 13 + 19 + 86 + 17, 0 falhas |

## Problemas em aberto e riscos

- Tile de 120 dp na camada longe repete o padrão a cada 120 dp (igual ao protótipo aprovado); se ficar visível no
  app, aumentar a densidade sorteada, não a velocidade.
- Custo: 156 pontos + até 2 capturas por quadro; medir junto com o da #586.
- Contorno da pílula muda a primitiva `AppStatusPill`: parâmetro opcional, desligado fora da HUD; contrato no design system.
