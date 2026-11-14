# Campo estelar mais rápido e fluxo de estrelas pela execução — issue #592

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-14,
branch `feat/issue-592-hud-star-flow`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido: as estrelas da HUD (#590) estão "muito lentas"; o anel cuja API está em execução deve consumir mais estrelas,
mais rápido. Rodada de 5 protótipos ajustáveis (`prototipos/issue-592/`: P1 cadência, P2 rajadas, P3 gravidade,
P4 espiral rápida, P5 fluxo) → **P5 · Fluxo** (`prototipos/issue-592/p5-fluxo/index.html`), com os valores do usuário:

| Parâmetro | Hoje (#590) | Escolhido |
|---|---|---|
| Velocidade das camadas perto / meio / longe | 3 / 1,5 / 0,5 dp/s | ×4,0 → 12 / 6 / 2 dp/s |
| Captura de anel sem execução | 1 a cada 8 s, rodízio por todos os anéis | 1 a cada 30 s, rodízio pelos anéis **sem** execução |
| Anel em execução | igual aos outros | filamento contínuo de 1,5 estrela/s (intensidade 100 %, só liga/desliga) |
| Duração do voo | 2 600 ms | 3 000 ms (lampejo de 200 ms mantido) |

O indicador antigo de execução (arco azul em órbita do `AppGargantuaRing`) **continua**: com "reduzir animações" o
campo para e é ele que diz qual anel está em execução (pedido do usuário).

## Fatos confirmados (2026-11-14, `6f2123f2`)

- Camadas: `HUD_STARFIELD_LAYERS` (`HudStarfield.kt:77-79`), speed 3/1,5/0,5; ladrilho `spec.speed * 240`
  (`HudStarfield.kt:218`) — **depende da velocidade**: multiplicar a velocidade sem separar o ladrilho rarefaz o campo
  (medido no protótipo P1, `L.tile = L.v·240` com v escalado).
- Capturas: `hudStarCaptures(timeMs, wells, forbidden)` (`HudStarCapture.kt`), 4 000 + n·8 000 ms, 30 por laço,
  poço `n mod wells.size`, voo 2 600 ms (`grep -n "HUD_STAR_CAPTURE_" HudStarCapture.kt`).
- Sinal de execução: `sessionActive` booleano por conta (`HudModel.kt:67`, `:404`); não existe intensidade.
- Arco de execução: `activity = gargantuaPhase(moving && active, …)` e `if (active) drawGargantuaActivity(…)`
  (`AppGargantuaRing.kt:68`, `:148`) — desenhado parado com movimento reduzido; o campo vai a `timeMs = 0`
  (`HudStarfieldCanvas.kt:60-62`).
- P5: braço de espiral por anel `θ = 0,6 + j·0,9 ± 0,125`; ociosos em rodízio pelos anéis sem execução
  (`prototipos/issue-592/p5-fluxo/index.html:538`, `:547-558`).

## Decisões

- Ladrilho separado da velocidade: `tile = velocidade_base · 240 s` (720 / 360 / 120 dp, os de hoje); laço de 240 s
  continua sem salto (60 s de período por camada divide 240 s).
- Agenda pura em função do tempo (como na #590): fluxo do poço ativo em grade `k · 1000/1,5 ms` com braço por poço;
  o poço carrega a janela de atividade (`activeSinceMs`, `activeUntilMs`), e só nasce captura dentro dela — ligar não
  faz estrela surgir no meio do voo, desligar deixa os voos em curso terminarem.
- Ociosos: 4 000 + n·30 000 ms, rodízio só pelos poços sem execução; nenhum poço ocioso, nenhuma captura ociosa.
- Sem intensidade nova: o P5 foi escolhido com "só liga/desliga"; `sessionActive` basta.

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/fluxo-estrelas-hud-592-execucao.md` |
| A02 | Velocidade ×4 com ladrilho fixo | `HudStarfield.kt`, `HudStarfieldTest.kt` |
| A03 | Ociosos a cada 30 s só pelos poços sem execução; voo 3 000 ms | `HudStarCapture.kt`, `HudStarfield.kt` (poço), `HudStarCaptureTest.kt` |
| A04 | Fluxo de 1,5 estrela/s no poço em execução, braço por poço, janela de atividade | `HudStarCapture.kt`, `HudStarCaptureTest.kt` |
| A05 | Canvas e HUD: `sessionActive` → janela de atividade do poço; arco de execução segue com movimento reduzido | `HudStarfieldCanvas.kt`, `HudNotch.kt`, `HudNotchTest.kt` |
| A06 | Docs: `hud-notch.md`, design system, protótipo | `docs/` |

## Ponto de situação

| Id | O que foi feito | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos, escolha (P5) e decisões | Sem harness (texto): `gh issue view 592` → issue aberta com o plano curto |
| A02 | `HudStarfield.kt`: `HudStarfieldLayerSpec.tile` separado de `speed`; 12 / 6 / 2 dp/s com ladrilhos 720 / 360 / 120 dp; 156 estrelas e laço de 240 s mantidos (Haiku, `frontend-dev`) | Vermelho: `HudStarfieldTest.kt:145` `deslocamento 3.0 dp na camada NEAR, esperado 12.0`; `:133` `volta de 60 s na camada NEAR. Expected <5.0> … actual <185.0>`. Verde: `gradlew.bat desktopTest --tests "*HudStarfieldTest" --tests "com.usagemonitor.architecture.*"` → 20 + 17, 0 falhas |
| A03 | `HudStarfieldWell` ganha `activeSinceMs`/`activeUntilMs` (semiaberto); ociosos em 4 000 + n·30 000 ms (8 por laço), poço `idle[n mod idle.size]` escolhido no início **absoluto** da captura (`timeMs − idade`); voo 3 000 ms. Devolvida 1× (janela comparada no eixo do laço) (Haiku, `frontend-dev`) | Vermelho: `uma cabeça em voo em t=6999 expected:<1> but was:<0>`; `captura ociosa no poço 1, poço 0 em execução: … veio d=226.91139 com R=15.0`; após devolução `poço 0 em execução no tempo absoluto, captura deve ir ao poço 1: … veio d=228.69469`. Verde: `gradlew.bat desktopTest --tests "*HudStarCaptureTest" --tests "*HudStarfieldTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.architecture.*"` → 20 + 20 + 86 + 17, 0 falhas |

## Problemas em aberto e riscos

- Custo: 1,5 estrela/s com voo de 3 s dá ~5 voos por poço ativo, cada um com rastro; medir junto com o da #590.
- Vários anéis em execução ao mesmo tempo multiplicam os voos (7 anéis → ~32); sem teto até medir.
