# Buraco negro das minimizadas: clique, órbita, filamento e céu de estrelas — issue #602

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-16,
branch `feat/issue-602-hud-orbit`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#602): a lista do buraco abre por hover e devia abrir por **clique**; depois de Restaurar, o balão continua na
conta restaurada e devia **voltar à lista** já sem ela; os glifos das minimizadas devem **orbitar** o buraco; minimizada
em execução deve mostrar o buraco **atraindo**; as estrelas "parecem flocos de neve" e devem parecer estrelas, com
**poeira estelar**. Rodadas: AY (5 opções, `prototipos/issue-602/index.html`) → **AY5 · Comboio orbital**; rodada 2 com
parâmetros (`ay5-r2-parametros/`); rodada 3 só do fluxo (`az-fluxo-poeira/`) → **AZ2 · Filamento de maré**.

Parâmetros fixados pelo usuário (JSON da rodada 3, unidades medidas em `az-fluxo-poeira/index.html`):

| Chave | Valor | Significado no protótipo |
|---|---|---|
| `stars` | 120 | estrelas; raio 1–1,5 dp (28% grandes 1,6–2,0) × `starSz` 1 (`:213-214`, `:519`) |
| `starBri` | 20 | alfa = alfa base (0,45–0,8; grandes 0,9) × 0,20 (`:522`) |
| `twin` | 2 | período da cintilação = (3–6 s) × 2 / 4,5; brilho 0,8 + 0,2·sen (`:518`) |
| `drift`, `dir` | 1, 0 | deriva de 1 dp/s a 0° (horizontal), sem queda (`:450`, `:514`) |
| `grains`, `grainA`, `grainSz` | 600, 2, 0,5 | poeira: 600 grãos, alfa (0,05–0,25) × 2, raio 0,5 dp (`:218`, `:536-540`) |
| `turn`, `gap`, `orb`, `arc` | 60, 40, 45, 5 | comboio: volta de 60 s, 40° entre glifos, raio 45 dp, arco de 5° atrás (`:364-365`, `:493`, `:679`) |
| `flow`, `flowStyle` | true, AZ2 | filamento de maré da conta em execução até o horizonte (`:571-586`) |
| `rate`, `dur`, `turns`, `trail`, `flash`, `reach` | — | **sem efeito**: `reach` 1,1 < 1,2 esvazia a faixa de captura (`:459`); o usuário escolheu só o filamento |
| `slow` | 3 | câmera lenta do protótipo; **não entra no app** (decisão do usuário) |

## Fatos confirmados (2026-11-16, `b91c0384`)

- Hover abre a lista: `HudBlackHoleCell.kt:46-54` (`hoverable`, `onHovered`) → `HudNotch.kt:308-311` (`onHoleHovered`).
  Linha da lista: hover com 160 ms (`HudBlackHoleList.kt:37`, `:79-86`), clique `:93`.
- Estado: `balloonIndex` `HudNotch.kt:199`; `holeIndex = accounts.size` e `holeSelection` `:203-204`; reset `:205-208`.
- Restaurar: `HudWindow.kt:367-375` → `:508-514` → `toggleMinimize` `:181-185`; **não** zera `holeSelection`, então o balão
  fica na conta restaurada (`HudBlackHoleBalloon.kt:38`, `HudNotch.kt:476`) — causa do segundo ponto da issue (leitura).
- Célula do buraco: `HudBlackHoleCell.kt:36-42`, recebe só `count`; `sessionActive` existe em `HudModel.kt:67`/`:406` e chega
  ao canvas por `hudStarRunningRings` (`HudStarfieldCanvas.kt:170-171`).
- Céu de hoje: três camadas caindo em +y a 12/6/2 dp/s (`HudStarfield.kt:93-97`, `:221-225`), rastro 5 px (`:105`,
  `:270-279`), brilho em cruz nas 6 maiores (`:127-130`; desenho `HudStarfieldCanvas.kt:109-115`).
- Linhas: `HudNotch.kt` 703, `HudRingStrip.kt` 609, `HudWindow.kt` 547, `HudStarfield.kt` 429, `HudStarCapture.kt` 208.

## Decisões

- Só o filamento AZ2 representa o consumo; **sem captura** de estrela pelo buraco. Capturas dos anéis completos (#592)
  ficam como estão.
- O céu novo substitui as três camadas que caem: deriva horizontal de 1 dp/s, sem rastro e sem cruz; os poços seguem
  desviando estrelas e as caixas proibidas seguem valendo.
- Cada quadro é função pura em `commonMain` com teste em `commonTest`; o canvas só desenha. Laço contínuo só com
  `AppMotionPolicy.continuous` e sem "reduzir animações" (quadro t = 0, sem filamento).
- A geometria da célula do buraco cresce de forma **estática** para conter a órbita (nunca por quadro).

## Atividades

| ID | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Lista do buraco abre só por clique/Enter; hover não abre; clique fora e Esc fecham |
| A03 | Restaurar no balão do buraco volta à lista sem a conta; fecha se o buraco esvaziar |
| A04 | Céu novo (quadro puro): 120 estrelas + 600 grãos, deriva horizontal, cintilação, sem rastro/cruz; canvas |
| A05 | Órbita em comboio (quadro puro) e geometria estática da célula que a contém |
| A06 | Glifos em órbita desenhados na célula; conta em execução destacada (não só por cor) |
| A07a | Filamento de maré AZ2: quadro puro `hudBlackHoleFilament` |
| A07b | Filamento desenhado na célula para cada conta em execução |

Cada atividade leva no mesmo commit `docs/hud-notch.md`, o design system e o protótipo visual quando muda a tela.

## Ponto de situação

| ID | Execução | Resultado |
|---|---|---|
| A01 | Sem harness (texto): protótipos AY1–AY5, AY5 r2 e AZ conferidos por captura headless e CDP (`ay*-qa.mjs`, `az-qa.mjs`, zero erros de JS); escolhidos AY5 + AZ2 com o JSON do usuário | Plano aprovado para execução |
| A02 | `HudBlackHoleCell` troca `hoverable` por `clickable` (clique/Enter alternam); `HudBlackHoleList` perde a seleção por hover de 160 ms; `HudNotch`: Esc e clique no corpo fora do buraco fecham. Medido: o ponto (2,2) do teste caía fora do corpo arredondado do notch e não chegava ao gesto; o teste clica no meio da faixa acima do buraco. Devolvida 1× (verde com 1 falha) (Haiku, `frontend-dev`) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHole*"` → `AssertionError: ... Did not expect any node but found '1' node that satisfies: (Text ... contains 'Anthropic — Alfa')` (hover no buraco abria a lista); Enter/Esc: `the node is missing [RequestFocus]`. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.HudBlackHole*" --tests "com.usagemonitor.architecture.*"` → 793 casos, 0 falhas (`test-runner`) |
| A03 | `HudNotch`: seleção do buraco que sai de `holeMembers` é descartada (`LaunchedEffect(holeMembers)` + `takeIf` no repasse ao balão); buraco vazio fecha o balão. Bug reproduzido antes da correção com estado elevado no teste (Haiku, `frontend-dev`) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleBalloonTest"` → 3/16 falham, ex. `AssertionError: ... Did not expect any node but found '1' node that satisfies: (Text ... contains 'Anthropic — Charlie')` (`HudBlackHoleBalloonTest.kt:149`, conta restaurada seguia no balão). Verde: recorte `ui.*` + `HudBlackHole*` + `architecture.*` → 796 casos, 0 falhas (`test-runner`) |
| A04 | `HudStarfield.kt` (429 → 377 linhas): uma camada de 120 estrelas com deriva horizontal de 1 dp/s e 600 grãos parados de `AppGargantuaTokens.dust`; sem queda, sem rastro, sem brilho em cruz; cintilação 0,8 + 0,2·sen em períodos 1,5/1,6/2,0/2,4 s (divisores do laço de 240 s); `HudStarfieldCanvas` desenha grãos e pontos. Feita em worktree paralela (`feat/issue-602-sky`) e trazida por cherry-pick (Haiku, `frontend-dev`, 3 agentes por limite de turnos) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.HudStarfieldTest"` → 12/23 falham, ex. `estrelas expected:<120> but was:<156>` (`HudStarfieldTest.kt:50`), `brilhos em cruz expected:<0> but was:<6>` (`:178`), `graos expected:<600> but was:<0>` (`:184`). Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.*" --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*"` → 911 casos, 0 falhas (`test-runner`) |
| A05 | `hudBlackHoleOrbit` (`HudBlackHoleOrbit.kt`, 9 casos); célula do buraco 114 dp (`HUD_BLACK_HOLE_CELL_SIZE`) na altura e no piso de largura da borda vertical com buraco (`HudNotchGeometry.kt`, 736 linhas); `HudBlackHoleCell` mede 114 dp com o anel centrado. Testes antigos atualizados por medirem o buraco de 64 dp: `HudNotchGeometryTest` (1), `HudNotchTest` (1), `HudAutoMinimizeTest` (4 fixtures: teto agora sai de `verticalCollapsed` com as contas esperadas; com o buraco de 114 dp + linha uma conta curta não basta, então os casos recolhem duas) (Haiku, `frontend-dev`, 4 agentes por limite de turnos) | Vermelho: `gradlew.bat desktopTest --tests "*HudBlackHoleOrbit*" --tests "com.usagemonitor.HudBlackHoleGeometryTest"` → 10/20 falham, ex. `AssertionError: expected:<114.0.dp> but was:<64.0.dp>` (`HudBlackHoleGeometryTest.kt:99`), `expected:<498.0.dp> but was:<448.0.dp>` (`:38`), `uma posicao por membro ... expected:<10> but was:<0>`. Verde: recorte `ui.*` + `HudBlackHole*` + `HudNotchGeometryTest` + `HudAutoMinimizeTest` + `*HudBlackHoleOrbit*` + `architecture.*` → 859 casos, 0 falhas (`test-runner`) |
| A06 | `HudBlackHoleOrbitGlyphs.kt` (187 linhas): miniaturas de 24 dp em órbita, arco, halo e anel da conta em execução; `HudBlackHoleCell` recebe os membros (`HudRingStrip` passa `holeMembers`); `AppGargantuaTokens.lens` espelha `--gargantua-lens` (não é token novo). Cena 3f do protótipo e `AppHudBar.prompt.md` atualizados (Haiku, `frontend-dev` + `prototype-dev`, 3 agentes por limite de turnos) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleCellTest"` → `AssertionError: Failed to assert count of nodes. Reason: Expected '4' nodes but could not find any node that satisfies: (TestTag = 'hudBlackHoleOrbitGlyph')` (`HudBlackHoleCellTest.kt:88`); `Expected exactly '1' node ... (ContentDescription contains 'em execução')`. Verde: recorte `ui.*` + `HudBlackHole*` + `architecture.*` → 802 casos, 0 falhas (`test-runner`) |
| A07a | `hudBlackHoleFilament` (`HudBlackHoleFilament.kt`, 8 casos): 48 passos da órbita (45 dp) ao horizonte (20 dp), ângulo `θ − 1,1·s + 0,035·sen(9π·s − 2,2·t)·sen(π·s)`, alfa `0,75·(1−s)^0,8`, largura `1,1 − 0,5·s` dp, mistura de cor = s; segmento só com raio final ≥ 20,5, então 47 segmentos (o último toca o horizonte, como no protótipo `:575`). Feita em worktree paralela e trazida por cherry-pick (Haiku, `frontend-dev`) | Vermelho (stub vazio): `gradlew.bat desktopTest --tests "*HudBlackHoleFilament*"` → `AssertionError: passos j=1..47 têm raio >= 20,5 ... expected:<47> but was:<0>` (`HudBlackHoleFilamentTest.kt:37`). Verde: `gradlew.bat desktopTest --tests "*HudBlackHoleFilament*" --tests "com.usagemonitor.architecture.*"` → 36 casos, 0 falhas (`test-runner`) |

## Problemas em aberto e riscos

- Medido no A05: a célula do buraco passou a 114 dp e a barra lateral com buraco ganhou piso de 114 dp; auto-minimizar
  pode recolher uma conta a mais que antes (o buraco custa mais que uma conta curta).
- 720 pontos por quadro no canvas (120 estrelas + 600 grãos): custo a medir no A04.
- Mudança em host de janela não é exercitada por teste (#342): o PR diz o risco.
