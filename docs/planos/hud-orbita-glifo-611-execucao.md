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
- **Escolha do usuário (2026-11-16): P1 · Glifo 32 sem cena, raio 90** (`prototipos/issue-611/p1-glifo-32/index.html`,
  `:325-344`). Glifo: disco de 32 dp em `AppGargantuaTokens.core` (#030508), sem cena Gargantua; marca real de 20 dp em
  `AppGargantuaTokens.hot` (#FFF0CB); contorno de 1,2 dp no raio 15,4 dp — contínuo na cor da lente em execução,
  tracejado 2/2 dp na ociosa; arcos de cota **fora** do disco, um anel por cota, traço 2,5 dp, folga 1,5 dp, o i-ésimo no
  raio 16 + 1,5 + 1,25 + i·4 dp, começando em −90°, trilho de fundo em volta inteira. Raio externo do glifo com 3 cotas:
  28 dp. Raio da órbita 90 dp; célula = 2·(90 + 28) = 236 dp. Passo, volta, rastro de 5° e filamento AZ2 seguem a #602;
  o filamento sai do raio da órbita (não mais de 45 dp fixos).
- Céu e poeira seguem o JSON da #602 (120 estrelas, `starBri` 20, 600 grãos, `grainA` 2, `grainSz` 0,5, deriva 1 dp/s a
  0°) — o usuário confirmou que **não aparecem** na célula do buraco (captura do app enviada no chat em 2026-11-16).

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-orbita-glifo-611-execucao.md` |
| A02 | Protótipos com direções distintas; escolha do usuário | `prototipos/issue-611/` (fora do versionamento) |
| A03 | TDD da geometria escolhida (glifo, raio, célula, sem sobreposição) | `HudBlackHoleOrbit.kt`, `HudBlackHoleGeometry.kt` |
| A04 | Desenho do glifo escolhido + `docs/hud-notch.md`, protótipo e design system | `HudBlackHoleOrbitGlyphs.kt`, docs |
| A05 | Céu e poeira dentro da célula do buraco: o poço do buraco usa o anel central, não a célula | `HudStarfieldCanvas.kt`, `HudNotch.kt` |

## Ponto de situação

| Id | Situação | Comando e resultado |
|---|---|---|
| A01 | feito | sem harness de teste (documento); validado pela leitura dos arquivos citados em "Fatos confirmados" |
| A02 | feito | sem harness (protótipo); P1–P4 conferidos por captura headless do Chrome (`--screenshot`, escuro/claro, 1x/2x, n=7 e n=10); escolhido P1 com raio 90 |
| A03 | feito | Raio 90 dp, disco 32 dp, `HUD_BLACK_HOLE_GLYPH_EXTENT` 28 dp, célula 2·(90+28) = 236 dp; filamento parte do raio da órbita. Vermelho: `gradlew.bat desktopTest --tests "*HudBlackHole*" --tests "*HudNotchGeometry*" --tests "*HudAutoMinimize*"` → 9 falhas na asserção (`HudBlackHoleOrbitTest.kt:18`, `:22`; `HudBlackHoleFilamentTest.kt:20`; `HudBlackHoleGeometryTest.kt:37`, `:98`, `:111`). Verde: mesmo comando → 123 testes, 0 falhas. Medido por `verticalCollapsed`: conta 110 dp na coluna, buraco 262 dp; o auto-minimizar (#596, regra intacta) só encurta a coluna a partir de 3 contas recolhidas — 4 cenários do `HudAutoMinimizeTest` refeitos com tetos de `alturaCom` |
| A05 | feito | `hudStarfieldWells` limita o raio do poço ao anel central (`maxRadiusPx` = `HUD_RING_SIZE`/2), passado pelo `HudStarfieldCanvas`; área proibida e `onRingPlaced` intactos. Vermelho (parâmetro ignorado): `gradlew.bat desktopTest --tests "*HudBlackHoleStarfieldTest*"` → `HudBlackHoleStarfieldTest.kt:43` "estrelas visiveis na faixa 35 a 57 px da celula: 0 (esperado > 0)" e `:54` "graos … 0". Verde: `gradlew.bat desktopTest --tests "*HudStar*" --tests "*HudBlackHole*" --tests "*HudNotch*"` → 257 testes, 0 falhas. Guarda: poço de conta de 64 dp idêntico com o limite (`HudStarActivityWindowTest`) |

- Céu ausente na célula (leitura, A05): o poço do buraco nasce do retângulo de `onRingPlaced`, que em
  `HudBlackHoleCell.kt:55` é a caixa da célula inteira (114 dp), não o anel de 64 dp; `hudStarfieldWells`
  (`HudStarfieldCanvas.kt:119-131`) dá raio `width / 2` = 57 dp, e o sorteio recusa ponto a menos de raio + 3 dp
  (`HudStarfield.kt:240`): sobra só o canto da célula. A causa só fica confirmada pelo teste vermelho da A05.

## Problemas em aberto e riscos

- Com 10 membros e dois vizinhos de 3 cotas, a corda a 36° no raio 90 (55,6 dp) fica 0,4 dp abaixo de 2 × 28 dp:
  os trilhos externos encostam. Aceito pela escolha do raio 90.

- Célula maior que 114 dp alarga a coluna vertical e muda a conta do auto-minimizar (`HudAutoMinimizeTest`, #600).
- Hipótese não confirmada: os glifos "▯" da captura podem ser a marca deformada pela cena Gargantua em 24 dp; só a
  captura real com a fonte identificada confirma.
