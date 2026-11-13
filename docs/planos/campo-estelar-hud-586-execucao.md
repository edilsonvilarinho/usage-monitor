# Campo de estrelas com paralaxe no fundo da barra HUD — issue #586

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-13,
branch `feat/issue-586-hud-starfield`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O usuário achou a barra HUD "sem vida" e pediu animação no tema espacial. A rodada AS (15 opções, ícones interagindo
quando em execução) foi recusada inteira; o pedido foi refeito: **só o fundo**, sem mexer no indicador de execução,
com estrelas em paralaxe e cada anel tratado como buraco negro, funcionando em todos os temas. Na rodada AT (10
opções, `prototipos/issue-586-r2/`) o usuário escolheu a **AT1 · Paralaxe em três camadas**; para o tema claro, na
rodada seguinte (`prototipos/issue-586-r2/at1-claro/`), a **L1 · Tinta densa**.

## Fatos confirmados (2026-11-13, `e048d361`)

- Corpo do notch: `Modifier.gargantuaHorizonBody` (`GargantuaHorizonBody.kt:77-84`), aplicado em `HudNotch.kt:259` e
  `HudRetractedStrip.kt:100`. Escuro/claro pela luminância da `surface`: escuro (13 presets) = `#07080B`
  (`AppGargantuaTokens.horizon`); claro (13 presets) = `preset.surface` + `appSheen()`.
- O que já anima na HUD (todos atrás de `continuous && !reduced`, `AppGargantuaRing.kt:63`): disco 14 s, cometa de
  sessão 2,6 s, respiro 3,2 s, plasma 2,8 s, detritos J7 128 s, ondas R1 1,3 s, respiração do corpo M1 6 s
  (`GargantuaHorizonBody.kt:82`). Nada é desenhado entre as contas.
- Protótipo aprovado: 140 estrelas por palco (30 perto, 45 meio, 65 longe), só em áreas livres (fora das caixas de
  texto e dos anéis), laço de 82,5 s; dentro de 2×R de um anel as estrelas de perto e meio desviam em curva com rastro
  de 4–6 px. Claro: tinta `#2A2622`, alfa 0,95/0,7/0,45, raios ×1,5.

## Decisões

- O cálculo é uma função pura de quadro em `commonMain` (posição, raio, alfa e rastro de cada estrela dado o tempo, o
  tamanho do corpo, os poços — centro e raio de cada anel — e as caixas proibidas de texto). Determinística por
  semente, para o teste e para não "piscar" entre recomposições.
- O desenho fica no corpo do notch, depois do fundo e antes dos anéis; não muda tamanho de nada.
- Contínuo só com `AppMotionPolicy.continuous`; reduzido = quadro fixo (o campo continua visível, parado).
- Barra recolhida (`HudRetractedStrip`) não ganha campo: é uma faixa de 10 dp.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Quadro puro do campo + testes |
| A03 | Desenho no notch por tema, com gate de movimento + teste de tela |
| A04 | Docs: `hud-notch.md`, design system, protótipo, histórico da skill `usage-monitor-visual-options` (rodadas AS e AT) |

## Ponto de situação

| Id | O que foi feito | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos e escolhas do usuário | `gh issue comment 586` → plano curto publicado |
| A02 | `HudStarfield.kt` (`commonMain`, sem Compose): `hudStarfieldStars` nasce 140 estrelas (30/45/65) por semente fixa, só em áreas livres; `hudStarfieldFrame` dá posição, raio, alfa e rastro por quadro — deriva por camada com laço exato de 82,5 s, deflexão perto dos poços (sombra 0,62 R, influência 3 R, razões do protótipo) com rastro de 5 px só em perto/meio, alfa 0 dentro de caixa proibida. 18% das estrelas marcadas `gold` para a A03 colorir | Vermelho: `java.lang.AssertionError: expected:<140> but was:<0>` (stub vazio; 8 testes vermelhos). Verde: `./gradlew.bat desktopTest --tests '*HudStarfieldTest'` → 8 testes, 0 falhas |
| A03 | `HudStarfieldCanvas` (arquivo próprio, tag `hudStarfield`) desenha o quadro da A02 atrás dos anéis com `drawWithCache`; poços = anéis medidos no `onRingPlaced` que o notch já tinha (`HudRingHitBoxes.measureRing`), guardados em `mutableStateMapOf` — com mapa comum o canvas ficaria sem poços; caixas proibidas = caixa do item menos o anel (só rótulos e pílula); cor por função pura `hudStarfieldInk`: escuro `AppGargantuaTokens.hot`/`gold`, claro tinta `--fg` `#171414` (não há token `#2A2622`) com alfa da L1 0,95/0,7/0,45 e raios ×1,5; tempo por `withFrameMillis` só com `continuous && !reduced` | Vermelho: `HudStarfieldTest.kt:148` (tinta: `expected Color(1.0, 0.94, 0.80) but was Color(0,0,0,0)`), `HudNotchTest.kt:263` (`hudStarfield` ausente), `HudStarfieldTest.kt:192` (recorte) e `HudNotchTest.kt:272` (`um poço por conta expected:<2> but was:<0>`, que expôs o mapa sem estado). Verde: `./gradlew.bat desktopTest --tests '*HudStarfieldTest' --tests 'com.usagemonitor.ui.HudNotchTest' --tests 'com.usagemonitor.architecture.*'` → 13 + 81 + 17 testes, 0 falhas. Sem teste: o redesenho com animação reduzida depois de reordenar |
| A04 | Docs: `hud-notch.md` ("Corpo: campo de estrelas com paralaxe"), `compose-implementation.md` (bullet em Motion, com o porquê do `mutableStateMapOf`), protótipo (nota na seção da HUD), histórico da skill `usage-monitor-visual-options` (linhas AS — recusada — e AT; letras até AT). Rebase sobre a `main` com o #584: conflitos em `HudNotch.kt`/`HudNotchTest.kt` resolvidos mantendo os dois; `HudNotch` voltou a 300 linhas | Sem harness (texto e mockup): âncoras com contagem única. Pós-rebase: `./gradlew.bat desktopTest --tests '*HudStarfieldTest' --tests 'com.usagemonitor.ui.HudNotchTest' --tests 'com.usagemonitor.architecture.*' --tests '*CardDragRuleTest'` → 1ª rodada `HudNotch.kt::HudNotch: 301 linhas (limite 300)`; depois 13 + 85 + 17 + 7, 0 falhas |

## Problemas em aberto e riscos

- Custo: 140 pontos por quadro a 60 fps no notch. Medir na A03 (tempo de quadro com e sem o campo).
- O claro fica legível só com a tinta densa; a superfície de cada preset claro muda pouco (`#F5FFFB`…`#FFFFFF`).
