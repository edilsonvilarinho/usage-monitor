# Buraco negro das minimizadas também na borda horizontal — issue #612

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-16,
branch `feat/issue-612-horizontal-black-hole` (worktree `.claude/worktrees/issue-612`, base `origin/main` `cc37bd08`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#612, com capturas): "o mesmo comportamento vertical quando estiver minimizado tem que aplicar no horizontal".
Na borda de cima e de baixo, as contas minimizadas passam a ir para o buraco negro (#600, #602, #611), como na lateral.
Segundo pedido: "não precisa desse texto indicando quantidades de APIs minimizadas". O rótulo "N minimizadas" sai da
célula do buraco nas duas bordas.

## Fatos confirmados (2026-11-16, `cc37bd08`)

Todos lidos nos arquivos citados (varredura do agente `explorer`, conferida por `Grep`).

- O buraco é desligado na horizontal por retorno antecipado: `HudBlackHole.kt:53-55` (`if (edge.isHorizontal)` →
  `holeMembers = emptyList()`). Quem chama: `HudNotchGeometry.kt:205`, `:442` (`verticalCollapsed` fixa `HudEdge.LEFT`),
  `HudNotch.kt:207`, `HudRingStrip.kt:117`.
- Faixa horizontal: `horizontalCollapsed` (`HudNotchGeometry.kt:401-434`). Altura = anel de 64 dp + linha de foco, mais
  8 dp de padding de cada lado. A conta minimizada mede `minimizedRingWithFocusWidth` (`HudNotchMinimized.kt:14-15`):
  anel de 28 dp + 6 dp + a cota em foco. Vão de 12 dp entre itens.
- A célula do buraco (`HUD_BLACK_HOLE_CELL_SIZE` = 236 dp, `HudBlackHoleGeometry.kt:23`) só entra na conta da vertical
  (`HudNotchGeometry.kt:464`, `:482`).
- Desenho: a horizontal desenha todos os índices como `HudRingItem` (`HudRingStrip.kt:137`, `:193-200`). A vertical
  põe `HudBlackHoleCell` depois da última conta (`:172-186`) e o voo em `HudBlackHoleFlights` (`:220`).
- Rótulo de contagem: `hudBlackHoleCountLabel` (`HudBlackHoleLabels.kt:6`), desenhado em `HudBlackHoleCell.kt:66-67`,
  também usado na semântica (`:49`, `:60`) e no cabeçalho da lista (`HudBlackHoleList.kt:47`). A largura do rótulo entra
  no piso da coluna vertical (`HudNotchGeometry.kt:467-474`).
- Auto-minimizar é só vertical: `HudAutoMinimize.kt:24`.
- Testes que fixam "horizontal inalterada": `HudBlackHoleTest.kt:75-81`, `HudBlackHoleCellTest.kt:56-61`,
  `HudNotchTest.kt:343-351` e `HudNotchGeometryTest.kt:619-624`. Esses testes mudam de sentido nesta issue.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-612/`, fora do versionamento). Nenhum Kotlin
  antes da escolha do usuário.
- O rótulo sai do desenho, mas a contagem continua na descrição semântica da célula: sem texto, o leitor de tela perde a
  quantidade. O cabeçalho da lista não muda, porque não foi pedido.
- Auto-minimizar segue só na vertical: o pedido é sobre as minimizadas, e a regra de 45% da horizontal não muda.
- **Restrição do dono (2026-11-16, captura `prototipos/issue-612/captura-gigante.png`):** a célula de 236 dp da #611
  "ficou gigante, não pode". O buraco tem de ser compacto nas duas bordas. As três primeiras direções (célula cheia,
  órbita inclinada larga, célula pendente) foram descartadas antes da entrega por partirem dos 236 dp.
- **Escolha do dono (2026-11-16): C1 · Órbita compacta** (`prototipos/issue-612/c1-orbita-compacta/index.html`,
  constantes em `:90-101`, glifo em `:327-342`). A mesma célula serve às duas bordas:
  - célula quadrada de 96 dp = 2·(raio 33 + extensão 15);
  - anel central de 40 dp (antes 64 dp, `HUD_RING_SIZE`), com o filamento terminando em 0,62·20 = 12,4 dp;
  - órbita de raio 33 dp, passo min(40°, 360°/n), volta de 60 s e rastro de 5° mantidos;
  - glifo = disco de 22 dp em `core`, contorno de 1,2 dp em r 10,4 dp (contínuo em execução, tracejado 2/2 ocioso), marca
    de 15 dp em `hot`;
  - **um único** arco de cota por fora do disco: a cota de maior fração usada (empate: a primeira na ordem), traço
    2,5 dp, folga 1,5 dp, raio 13,75 dp, trilho em volta inteira. Extensão = 13,75 + 1,25 = 15 dp;
  - com 10 membros, corda 2·33·sen 18° = 20,4 dp < 22 dp: os discos se sobrepõem 1,6 dp; as marcas de 15 dp não se
    tocam. Aceito pela escolha.
- Encaixe na janela: a célula de 96 dp ocupa o vão da conta **mais** o padding transversal (8 dp de cada lado, centrada
  por `requiredSize`). A espessura da faixa horizontal e a largura da coluna vertical sobem no máximo para
  96 − 2·8 = 80 dp de conteúdo. Com isso a faixa fica em 96 dp no total, como no protótipo.
- **Ajuste do dono após ver o app (2026-11-16):** com raio 33 e passo de 40° os glifos ficaram apertados (corda
  2·33·sen 20° ≈ 22,6 dp para glifo de 30 dp). Escolha: **volta inteira + raio 38**. Passo = 360°/n para todo n (deixa
  de ser comboio agrupado), raio 38 dp, célula 2·(38 + 15) = 106 dp, piso de conteúdo 106 − 16 = 90 dp. Com 7 contas a
  corda é 33 dp; com 10 contas é 23,5 dp, os discos não se tocam e só os arcos encostam.
- **Hover abre a lista do buraco (dono, 2026-11-16, depois de testar o app):** "o ideal é passar o mouse e ele já
  abrir". Reverte a decisão da #602 ("clicar e não colocar o mouse em cima"). Regra: a entrada do ponteiro no buraco
  abre a lista se o balão aberto não for o do buraco; se já for (lista ou conta escolhida nela), nada muda. O clique e o
  Enter seguem alternando. A seleção de linha da lista continua só por clique ou Enter.
- O voo do sumidouro (520 ms, `hudSinkFrame`) vale nas duas bordas: na horizontal, o deslocamento é ao longo da faixa.

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-buraco-horizontal-612-execucao.md` |
| A02 | Protótipos com direções distintas; escolha do usuário | `prototipos/issue-612/` (fora do versionamento) |
| A03 | TDD: geometria C1 (célula 96, anel 40, raio 33, glifo 22/marca 15, um arco), sem o rótulo de contagem no desenho e na conta da coluna (contagem segue na semântica) | `HudBlackHole*.kt`, `HudNotchGeometry.kt` |
| A04 | TDD: buraco na borda horizontal (arranjo, espessura da faixa, célula e voo ao longo da faixa) | `HudBlackHole.kt`, `HudNotchGeometry.kt`, `HudRingStrip.kt` |
| A05 | (absorvida pela A03: o rótulo sai junto com a geometria, que dependia da largura dele) | — |
| A06 | `docs/hud-notch.md`, `AppHudBar.prompt.md` e protótipo de tela | docs |
| A07 | TDD: hover no buraco abre a lista (reverte a regra de clique da #602) | `HudBlackHoleCell.kt`, `HudRingStrip.kt`, `HudNotch.kt` |
| A08 | TDD: linha da lista do buraco destacada sob o mouse (só destaque; balão segue no clique/Enter) | `HudBlackHoleList.kt` |

## Mapa da A04 (leitura do agente, 2026-11-16)

- A04a — arranjo: gate em `HudBlackHole.kt:53-55`; teste que fixava a horizontal em `HudBlackHoleTest.kt:75-85` passa a
  esperar `shown=[1]`, `holeMembers=[0,2]`, `hasHole=true` em TOP e BOTTOM.
- A04b — geometria: `horizontalCollapsed` (`HudNotchGeometry.kt`, ~400) usa `hudColumnArrangement` como a vertical:
  minimizadas saem, a célula soma `HUD_BLACK_HOLE_CELL_SIZE` + `HUD_ITEM_GAP` no comprimento e o conteúdo transversal
  tem piso `HUD_BLACK_HOLE_CELL_SIZE − 2·HUD_NOTCH_PADDING_ACROSS`. Testes que fixavam a horizontal:
  `HudNotchGeometryTest.kt:619-631`, `ui/HudNotchTest.kt:381-398`.
- A04c — desenho: ramo horizontal de `HudRingStrip.kt` (~196-200, ~216-230) desenha `arrangement.shown`, vão de 12 dp e
  `HudBlackHoleCell`; `hudFlightCenters` (`HudBlackHoleFlight.kt`) e `HudBlackHoleFlights` ganham `horizontal: Boolean
  = false` (eixo X, `Alignment.CenterStart` + `offset(x =)`). Testes: `ui/HudBlackHoleCellTest.kt:57-62` e `:81-87`;
  teste novo no `HudNotch`: borda de cima, clique no buraco abre a lista (o nome da conta minimizada aparece; ela não
  aparece mais na faixa).

## Ponto de situação

| Id | Situação | Comando e resultado |
|---|---|---|
| A01 | feito | sem harness de teste (documento); validado pela leitura dos arquivos citados em "Fatos confirmados" |
| A02 | feito | sem harness (protótipo); C1–C3 conferidos por captura headless do Chrome (`--screenshot`, n=7 e n=10, escuro/claro, 1x/2x); C1 e C2 devolvidos uma vez (anel central ausente; texto sob o anel e glifos cobrindo marcas), C3 uma vez (célula cortada em 2x); escolhido C1 |
| A03 | feito | Geometria C1 com o ajuste do dono (raio 38, passo 360°/n, célula 106 dp, anel 40, glifo 22/marca 15, um arco), rótulo fora do desenho, poço do campo estelar do buraco = 20 dp. Vermelho: `gradlew.bat desktopTest --tests "*HudBlackHole*" --tests "*HudNotch*" --tests "*HudAutoMinimize*"` → 6 falhas na asserção (`expected:<33.0.dp> but was:<90.0.dp>`, `expected:<96.0.dp> but was:<236.0.dp>`, `altura do buraco, lateral expected:<236.0.dp> but was:<250.0.dp>`); fiação do poço `HudNotchTest`: `expected:<20.0> but was:<32.0>`; passo: `HudBlackHoleOrbitTest.kt:18` `passo com n=3 … esperado=2.094 obtido=0.698`. Verde: `gradlew.bat desktopTest --tests "*HudBlackHole*" --tests "*HudNotch*" --tests "*HudAutoMinimize*" --tests "*HudStar*"` → 272 testes, 0 falhas; `gradlew.bat allTests` (antes do ajuste de raio) → 0 falhas. Conferido no app local (`gradlew.bat run`) pelo dono na borda lateral |
| A04 | feito | Buraco nas bordas de cima e de baixo: `hudColumnArrangement` sem o parâmetro `edge`, `horizontalCollapsed` com a célula de 106 dp e piso transversal de 90 dp, ramo horizontal do `HudRingStrip` com célula e voo no eixo X. Vermelho: `gradlew.bat desktopTest --tests "*HudBlackHoleTest*" --tests "*HudNotchGeometryTest*" --tests "*HudNotchTest*"` → `HudBlackHoleTest.kt:81` `borda TOP expected:<[1]> but was:<[0, 1, 2]>`, `HudNotchGeometryTest.kt:627` `largura, topo expected:<146.0.dp> but was:<97.0.dp>`, `HudNotchTest.kt:383` `Expected exactly '1' node … (TestTag = 'hudBlackHole')`, depois `altura do buraco, topo expected:<106.0.dp> but was:<90.0.dp>`. Verde: `gradlew.bat desktopTest --tests "*HudBlackHole*" --tests "*HudNotch*" --tests "*HudAutoMinimize*" --tests "*HudStar*" --tests "*HudRing*"` → 277 testes, 0 falhas. O teste do voo no topo nasceu verde (contrato novo, sem regressão provada). Conferido no app local pelo dono no topo e na lateral |
| A07 | feito | Hover no buraco abre a lista (`onHoleHovered`: abre só se o balão aberto não é o do buraco; clique/Enter seguem alternando). Vermelho: `gradlew.bat desktopTest --tests "*HudNotchTest*"` → `HudNotchTest.kt:412` `nome 'INFORMATA2' da conta minimizada deveria aparecer na lista do buraco, esquerda`, `:427` `… topo`, `:442` `segundo hover fechou a lista do buraco; deveria mantê-la aberta`; `HudBlackHoleBalloonTest.kt:55` (regra da #602) invertido para "hover no buraco abre a lista". Verde: `gradlew.bat desktopTest --tests "*HudBlackHole*" --tests "*HudNotch*" --tests "*HudRing*"` → 218 testes, 0 falhas. Conferido no app local pelo dono |

## Problemas em aberto e riscos

- Coluna vertical com buraco cai de 236 dp para 96 dp de largura e a célula de 262 dp (célula + rótulo + vão) para
  96 dp: a conta do auto-minimizar (#596, `HudAutoMinimizeTest`) muda; recolher uma conta de 110 dp já encurta a coluna.
- Primeira minimização com o buraco vazio (as duas bordas, desde a #600): durante os 520 ms não há buraco composto
  (`hasHole` exige um membro com progresso 1), então `hudFlights` não tem alvo e a conta fica invisível no slot até o
  buraco nascer. Lido em `HudBlackHole.kt:40`, `HudRingStrip.kt:174` e `HudBlackHoleFlight.kt:34` (agente da A04c).
  Fora do escopo da #612; corrigir pede compor o buraco durante o voo sem estourar o tamanho segurado da janela.
- Glifos encostam na borda do notch (a célula usa o padding transversal): o contorno do notch não pode cortá-los.
- Mudança no tamanho da janela da HUD horizontal: o risco de sistema de janelas da #342 vale (suíte só no Windows).
