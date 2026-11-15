# Barra HUD vertical: contas minimizadas num único buraco negro — issue #600

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-15,
branch `feat/issue-600-hud-black-hole`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido: agrupar as contas minimizadas da barra HUD num único buraco negro; ao passar o mouse, ver quem está agrupado.
Rodada de 10 protótipos (`prototipos/issue-600/index.html`) → **P10 · sumidouro**, refinado em duas rodadas a pedido do
usuário (`prototipos/issue-600/p10-sumidouro-r2/index.html`):

- as minimizadas (manuais #576 e automáticas #596) saem da coluna e entram num buraco negro na base da barra, com a
  contagem embaixo ("4 minimizadas"); ao minimizar, a conta encolhe e desliza até o buraco, e a contagem sobe na chegada;
- hover (ou foco + Enter) no buraco abre a lista de quem está dentro (glifo, nome, valor em foco; "auto" na
  auto-minimizada);
- uma linha abre **o balão de conta de hoje** (`HudBalloon`, sem card novo) daquela conta, com "← N no buraco" no topo;
  o botão `+` "Restaurar" de hoje tira só aquela conta; a auto-minimizada segue sem botão e diz "volta sozinha quando couber".

## Fatos confirmados (2026-11-15, `6c48fc0a`)

- Célula minimizada: ramo `account.minimized` em `HudRingStrip.kt:416-432`; tamanho em `HudNotchMinimized.kt:14-19`.
- `HudAccount.minimized`/`autoMinimized` em `HudModel.kt:110-112`; `buildHudAccounts` em `HudModel.kt:345` (`:398`).
- Balão: estado `balloonIndex` em `HudNotch.kt:198` (sentinela `APP_BALLOON` para a engrenagem, `:205-210`); hover do anel
  `:295`; chamada `HudBalloon` `:398-405`; assinatura `HudBalloon(edge, tailCenter, modifier, content, bodyHeight, …)`
  (`HudBalloon.kt:140-148`).
- Botão Restaurar: `HudWindow.kt:485-513` (`hudShowsMinimizeButton`, `HudMinimizeAction.kt:10`); toggle e persistência
  `HudWindow.kt:180-184`, `HudMinimizedAccounts.kt:10-14`.
- Auto-minimizar mede com `verticalCollapsed` (`HudNotchGeometry.kt:424-465`; altura das minimizadas somada em `:440-449`)
  e aplica em `HudAutoMinimize.kt:44-54`, chamado em `HudWindow.kt:240`.
- Transição de minimizar 520 ms por conta: `HudMinimizeTransition.kt:29-50`.
- Tamanhos (`wc -l`): `HudNotch.kt` 739, `HudNotchGeometry.kt` 716, `HudBalloon.kt` 659, `HudRingStrip.kt` 562,
  `HudWindow.kt` 547 — os dois primeiros estão perto do teto de 800: código novo vai em arquivos próprios.

## Decisões

- **Só a borda vertical** (como #596). Em cima/embaixo as minimizadas seguem como hoje.
- **Buraco com 1 ou mais minimizadas**; com 0 não existe. Fica depois da última conta completa.
- **Ordem da lista**: a ordem de publicação das contas (total e determinística). O "mais recente primeiro" do P10
  exigiria persistir quando cada conta entrou; fica fora.
- **Detalhe = `HudBalloon` existente**, conteúdo da conta igual ao de hoje, com uma linha "← N no buraco" no topo; a cauda
  aponta o buraco (o anel da conta não está na coluna). Nenhum card novo.
- **Funções puras primeiro**: arranjo da coluna (`hudBlackHole…`) e quadro da transição em commonMain/desktopMain com
  teste; o desenho só consome.
- Texto PT/EN por objeto de rótulos (`localized`), nunca `== AppLanguage.PT` inline.

## Atividades

| ID | O quê |
|---|---|
| A01 | Este plano |
| A02 | Arranjo puro da coluna: completas + um buraco com as minimizadas (vertical), em TDD |
| A03 | Geometria: `verticalCollapsed` mede o buraco como uma célula; auto-minimizar coerente; TDD |
| A04 | Célula do buraco (anel + "N minimizadas") no lugar das minimizadas em `HudRingStrip`; teste de tela — **mesmo commit do A03**: geometria nova sem o desenho novo mede o que não desenha |
| A05a | Extração: `hudNotchMeasurePolicy` de `HudNotch.kt` para `HudNotchMeasure.kt` (sai da faixa de 750 antes de crescer) |
| A05 | Balão do buraco: lista; linha (hover/clique/Enter) abre o `HudBalloon` da conta com "← N no buraco"; Restaurar e "auto"; teste de tela |
| A06 | Transição "sumidouro": a conta desliza até o buraco e a contagem sobe na chegada; restaurar inverso; quadro puro em TDD |
| A07 | `docs/hud-notch.md`, design system (`AppHudBar.prompt.md`) e protótipo visual registram a regra |

## Ponto de situação

| ID | Execução | Resultado |
|---|---|---|
| A01 | Sem harness (texto): 10 protótipos conferidos por captura headless (`qa600.mjs`, zero erros de console); P10 escolhido; rodadas 2 e 3 a pedido do usuário; teste de interação da rodada 3 `r2-test.mjs` → 25 OK, 0 FAIL, `errors=0` | P10 rodada 3 aprovado |
| A02 | `hudColumnArrangement` + `HudColumnArrangement.hasHole` em `HudBlackHole.kt`; 6 casos em `HudBlackHoleTest` (Haiku, `frontend-dev`) | Vermelho (stub com todas na coluna): `gradlew.bat desktopTest --tests "com.usagemonitor.HudBlackHoleTest"` → 3/6 falham, ex. `AssertionError: expected:<[1, 4]> but was:<[0, 1, 2, 3, 4, 5]>` (`HudBlackHoleTest.kt:50`). Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.HudBlackHoleTest" --tests "com.usagemonitor.architecture.*"` → 6 + 28, 0 falhas |
| A03 | `hudBlackHoleCountLabel` (`HudBlackHoleLabels.kt`, por `localized`); `verticalCollapsed` usa `hudColumnArrangement`: minimizadas viram uma célula `HUD_RING_SIZE + HUD_WORD_LINE`, largura = maior rótulo **de uma linha** entre `AppLanguage.entries` (sem parâmetro de idioma nos ~70 chamadores), piso 64 dp. Devolvida 1× (rótulo medido em 2 linhas na largura e 1 na altura) (Haiku, `frontend-dev`) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudBlackHoleGeometryTest" --tests "com.usagemonitor.presentation.HudBlackHoleCountLabelTest"` → `expected:<448.0.dp> but was:<518.0.dp>` (`HudBlackHoleGeometryTest.kt:33`), `expected:<[1 minimizada]> but was:<[]>`; após a devolução `largura=91.0.dp rotulo=4 minimizadas umaLinha=89.0.dp` (`:62`). Verde: recorte `HudBlackHole*` + `HudNotchGeometryTest` + `HudAutoMinimizeTest` + `presentation.*` + `architecture.*` → 1 287 casos, 0 falhas |
| A04 | `HudBlackHoleCell` (anel sem arcos + contagem numa linha de `HUD_WORD_LINE`); `HudRingStrip` desenha `arrangement.shown` + buraco na vertical; poço/caixa do campo estelar pelo índice `accounts.size`; `HudNotchTest` da célula minimizada lateral passa a medir o buraco. Hover do buraco abre o balão do 1º membro até o A05 (Haiku, `frontend-dev`) | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudBlackHoleCellTest"` → `Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'hudBlackHole')` (`HudBlackHoleCellTest.kt:36`). Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.HudBlackHole*" --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.architecture.*"` → exit 0 |
| A05a | Refatoração pura: `hudNotchMeasurePolicy` movido para `HudNotchMeasure.kt` (127 linhas); `HudNotch.kt` 739 → 629; tipos auxiliares de `private` para `internal` (Haiku, `frontend-dev`) | Sem teste novo (comportamento inalterado): `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*"` → exit 0, 757 casos, 0 falhas (`test-runner`) |

## Problemas em aberto e riscos

- #342: mudança em host de janela (`HudWindow.kt`) sem medição no Linux — o PR declara o risco.
- `HudNotch.kt` (739) e `HudNotchGeometry.kt` (716) na faixa de 750: a atividade que tocá-los começa extraindo.
