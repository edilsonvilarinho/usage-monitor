# Cena 3D AL1 e marcas em relevo — issue #517

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`feat/issue-517-marks-3d-al1`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O dono achou os ícones das APIs na HUD "muito 2D". Duas rodadas de opções em HTML (skill
`usage-monitor-visual-options`): a AK (só o preenchimento da marca) não era o pedido; na AL (a cena inteira — buraco
negro e órbitas) ele escolheu **AL1 · Gargantua de Interstellar**, e respondeu "Relevo no anel e nos cards" para a
marca (relevo AK1 em todo lugar onde a marca aparece).

## Fatos confirmados (2026-10-28, `a768ae7d`)

- A marca é composta por fora do anel: `HudRingStrip.kt:215-237` põe `AppProviderMark` por cima de
  `AppGargantuaRing`, então nenhum traço do disco passa na frente dela. Lido no arquivo.
- `drawGargantuaCore` (`GargantuaDrawing.kt:26`) desenha brilho, lente (dois arcos finos: alfa 0,35 e 0,18), metade de
  trás do disco, horizonte, contorno e metade da frente com **o mesmo pincel** nas duas metades. Único uso:
  `AppGargantuaRing.kt:112`.
- `AppProviderMark` é uma `Image` com `ColorFilter.tint`: silhueta chapada. Usos: anel da HUD, balão (14dp,
  `HudBalloon.kt:287`), card (16dp, `ApiUsageCardHeader.kt:304`).
- Teste de pixel do anel: `GargantuaHudTest.kt` (`frames()` captura `AppGargantuaRing` sobre preto). Nenhum teste
  captura a marca nem compara a frente e o fundo do disco.
- HUD no navegador (`web/hud.html:63-115`) tem desenho próprio do núcleo, do disco e da marca (só Claude e OpenAI).
  O bot do Telegram (`TelegramRingsRenderer.kt`) desenha só núcleo e brilho, sem disco nem marca: fica fora.
- Cópias documentais: `docs/design-system/components/data/AppGargantuaRing.jsx` (disco 54-56, lente 91-92),
  `components/core/AppProviderMark.jsx`; protótipo `docs/planos/prototipo-visual-opencode.html` (SVG estático,
  1152-1199).

## Decisões

- **AL1 sem peça nova:** a lente vira o arco largo da imagem dobrada do disco de trás (raio 1,17 do horizonte, Doppler
  brasa → quente), com um arco fino por baixo; a metade de trás do disco fica mais fraca e a da frente mais larga e
  clara. Mesma fase (`orbitMillis`), mesmos filamentos, sem blur, sem partícula aleatória: o anel continua
  determinístico para captura.
- **Profundidade:** `AppGargantuaRing` ganha um encaixe central (`centerContent`) desenhado entre o horizonte e a
  metade da frente do disco. A marca deixa de ser irmã por cima e passa a morar dentro da cena.
- **Relevo AK1 na marca, em todo uso:** gradiente vertical (claro em cima, escuro embaixo), borda de cima à esquerda
  acesa e de baixo à direita na sombra, sombra curta deslocada. Sem blur (mesma regra do cenário). Cores por função
  pura testável.
- Arcos de cota, números e pílula não mudam; nenhuma duração nova.

## Atividades

- A01 este plano.
- A02 AL1 no `drawGargantuaCore` e na `GargantuaScene` (teste de pixel: frente mais clara que trás; arco de lente).
- A03 encaixe central no `AppGargantuaRing`; `HudRingStrip` passa a marca por ele (teste de pixel: o disco cruza a
  marca).
- A04 relevo no `AppProviderMark` (teste puro das cores e teste de pixel da borda acesa).
- A05 `web/hud.html` com AL1 e relevo.
- A06 design system, protótipo, `docs/hud-notch.md`, histórico da skill de opções visuais e `gargantua-ring.js`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness (documentação) | plano escrito a partir das leituras acima |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.GargantuaHudTest"` | 2 falhas na asserção: `frente 0.69803923 deveria passar de 1,3 × trás 0.5738562`; `topo do arco de lente 0.33202615 deveria passar de 0,5` (1ª medição, a 20°/340° com vizinhança de 1px, lia o antialiasing da frente na amostra de trás: ângulos e largura corrigidos e o vermelho refeito) |
| A02 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.GargantuaHudTest" --tests "com.usagemonitor.ui.GargantuaSceneTest"` | 12 testes, 0 falhas |
| A03 | vermelho (stub do `centerContent` por cima): `gradlew.bat desktopTest --tests "com.usagemonitor.ui.GargantuaHudTest"` | 1 falha na asserção: `o disco da frente deveria cobrir a marca embaixo do centro, azul 1.0` |
| A03 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.GargantuaHudTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.architecture.*"` | 92 testes, 0 falhas (11 + 64 + 17) |
| A04 | vermelho (stub com cores chapadas e espessura 0): `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.AppProviderMarkReliefTest" --tests "com.usagemonitor.ui.AppProviderMarkReliefScreenTest"` | 3 falhas na asserção: `expected:<1.0> but was:<0.0>`; `topo de Color(0.30980393, 0.54901963, 1.0, …) não clareou`; `borda de cima 0.5019608 deveria passar a de baixo 0.5019608 em 0,3` |
| A04 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.*" --tests "com.usagemonitor.ui.AppProviderMarkReliefScreenTest" --tests "com.usagemonitor.ui.GargantuaHudTest" --tests "com.usagemonitor.ui.HudNotchTest"` | 165 testes, 0 falhas |
| A05 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.WebHudPageTest"` | 1 falha na asserção: `ANTHROPIC sem marca`, `MINIMAX sem marca`, `CODEX sem marca`… (as 11 fontes) |
| A05 | verde: `gradlew.bat desktopTest --tests "com.usagemonitor.WebHudPageTest" --tests "com.usagemonitor.LocalWebAccessServiceTest" --tests "com.usagemonitor.WebAccessActionsTest"` | 12 testes, 0 falhas. O desenho AL1 da página não tem harness: conferido no Chrome com `hud.html` servido por um mock local de `/api/snapshot` (Claude, OpenCode Go, Codex, MiniMax), console sem erro, marca em relevo com o disco da frente por cima |
| A06 | sem harness (documentação, kit JSX, protótipo e skill) | `node -e "new Function(...)"` na `gargantua-ring.js`: sem erro de sintaxe; protótipo aberto no Chrome (17 anéis SVG convertidos por script), console sem erro, lente por cima e frente do disco cruzando a marca em relevo |

## Problemas em aberto e riscos

- A HUD é desenhada pelo sistema de janelas; nada aqui muda janela nem forma, só o conteúdo do `Canvas` (#342: risco
  baixo, mas o PR diz em que plataforma foi visto).
- AL1 é mais forte que o disco de hoje; o dono já recusou cenário "chamativo" (disco de 6 faixas). Os alfas vêm da
  galeria aprovada e não sobem além dela.
