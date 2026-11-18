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
- **Escolha do dono (2026-11-18): P05 · órbita radial**, com quatro mudanças (`prototipos/issue-619/briefing-r2.md`):
  o modo encaixado e a faixa recolhida de hoje ficam; o flutuante entra por um novo botão redondo na coluna dos botões
  (alfinete, mão, novo, engrenagem); campo expandido menor (~240 dp) com o fundo do corpo da barra HUD, sem gradiente
  dourado; disco de acreção do buraco girando (atrás de `AppMotionPolicy.continuous`); protótipo R2 com painel de
  parâmetros que exporta JSON, para o dono ajustar e devolver os valores.

## Atividades

| ID | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-flutuante-619-execucao.md` |
| A02 | 10 protótipos com direções distintas; escolha do dono | `prototipos/issue-619/` (fora do versionamento) |
| A03 | Preferências do modo flutuante | `HudWindowPreferences.kt` + `HudWindowPreferencesTest.kt` |
| A04 | Parâmetros e geometria pura do campo orbital | novo `HudOrbitGeometry.kt` + teste |
| A05 | Posição flutuante e plataforma suportada | novo `HudFloatingPlacement.kt` + teste |
| A06 | Botão "Soltar da borda" / "Encaixar na borda" | `HudHandles.kt` (ou arquivo novo) + teste de tela |
| A07 | Campo orbital e buraco minimizado (composables) | novos `HudOrbitField.kt`, `HudFloatingHole.kt` + testes |
| A08 | Host do modo flutuante na janela da HUD | `HudWindow.kt` + arquivos novos de estado/efeitos + testes de decisão |
| A09 | Documentação e design system | `docs/hud-notch.md`, `AppHudBar.prompt.md`, protótipo visual |

### Especificação por atividade

Parâmetros do dono (`prototipos/issue-619/parametros-dono.json`), valores padrão do modo: campo 180 dp, raio da
órbita 40 dp, anel da conta 28 dp, marca 8 dp, buraco minimizado 32 dp, inclinação do disco 29°, espessura do disco 3 %,
uma volta do disco a cada 3,5 s, uma volta da órbita a cada 180 s, fundo 100 % opaco, poeira densidade 100 % e brilho
200 %, borda 0 dp, rótulos `completo`, texto 8 sp, disco no token `hot`. Com rótulos completos o campo cresce até caber
os rótulos (no R2: 244 dp); nada é desenhado fora do campo.

- **A03.** `hudFloating: Boolean` (padrão `false`) e centro flutuante em px de tela (`hudFloatX`, `hudFloatY`; ausente =
  nunca posto). Mesmo padrão de `hudAutoRetract` (`HudWindowPreferences.kt:122-132`); teste de ida e volta como
  `HudWindowPreferencesTest.kt:144-156`.
- **A04.** `HudOrbitParams` com os padrões acima e `hudOrbitLayout(params, accountCount, labels, orbitPhase)` →
  diâmetro do campo, centro de cada conta (ângulo inicial −90°, passo 360°/n, sentido horário, ordem da lista),
  diâmetro do buraco minimizado. Ordem total e determinística.
- **A05.** `hudFloatingBounds(center, windowSize, workArea)` prende a janela dentro da área de trabalho e **nunca**
  encosta numa borda por conta própria; sem centro salvo, nasce no centro do notch atual. `hudFloatingSupported(os)`:
  só Windows (#340); outros sistemas não mostram o botão.
- **A06.** Botão redondo novo na ponta da engrenagem (fato: a ponta de perto já tem alfinete e mão; a de longe só a
  engrenagem, e `HUD_HANDLES_PER_SIDE = 2`, `HudNotchGeometry.kt:260`, já reserva a segunda vaga — a janela não cresce).
  Espelha a ponta de perto: engrenagem junto ao notch, botão novo por fora. Mesmo estilo dos de hoje, ícone de
  buraco com órbita; rótulo por objeto de rótulos (`localized`), "Soltar da borda" / "Release from edge" no encaixado e
  "Encaixar na borda" / "Dock to edge" no flutuante; ausente quando `hudFloatingSupported` é falso.
- **A07.** `HudOrbitField` (expandido: fundo do corpo da HUD, poeira, contas em `AppGargantuaRing` com arcos e marca,
  rótulos, buraco central) e `HudFloatingHole` (minimizado: buraco com disco inclinado girando só com
  `policy.continuous && !policy.reduced`, ponto de atualização quando `HudUpdateIndicator` existe). Animação infinita
  só atrás de `AppMotionPolicy` (trava o `waitForIdle`).
- **A08.** No modo flutuante, a mesma `HudWindowHost` troca o notch pelo campo: janela de tamanho fixo (campo + balão),
  recorte pelo `hudHitRegionMode`, hover de 200 ms abre o campo, arrasto livre persistido (A03/A05), clique numa conta
  abre `HudAccountBalloonContent`, engrenagem abre `HudWindowAppBalloon`, indicador de atualização igual ao de hoje
  (`hudUpdateIndicatorOf`). O botão novo alterna os modos. Nenhuma mudança no modo encaixado.
- **A09.** Seção "Modo flutuante" na `hud-notch.md`; contrato em `AppHudBar.prompt.md` (e correção da seção Gestures,
  que ainda cita o clique direito); tela no `prototipo-visual-opencode.html`.

## Ponto de situação

| ID | Estado | Execução |
|---|---|---|
| A01 | feito | sem harness (documento); conferido por leitura |
| A02 | feito | sem harness (protótipo); P01–P10 conferidos por captura headless do Chrome a 1400 px (escuro/claro); devolvidos uma vez P02–P08; escolhido P05. R2 (`prototipos/issue-619/r2-orbita-radial/`) devolvido uma vez (rótulos fora do campo, cena encaixada, botões) e aprovado: giro conferido por diferença de quadros `?t=0` × `?t=1500`, JSON de parâmetros e restauração por `#p=` conferidos |
| A03 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudWindowPreferencesTest*"` → 3 falhas de asserção em 22 (`HudWindowPreferencesTest.kt:161`, `:181`, `:190`); verde: mesmo comando, 22 testes, 0 falhas |
| A04 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudOrbitGeometryTest*"` com stub → falhas de asserção, entre elas `HudOrbitGeometryTest.kt:76` (a fase gira o anel); verde: mesmo comando, 13 testes, 0 falhas. Devolvida uma vez (nomes em português, declarações públicas) |
| A05 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingPlacementTest*"` com stub → 11 de 11 falhas de asserção (ex.: `HudFloatingPlacementTest.kt:41` `assertEquals(1520, bounds.xPx)`); verde: mesmo comando, 11 testes, 0 falhas. Assinatura recebe `notchCenter` para o primeiro uso |
| A06 | feito | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"` com stub → 4 falhas de asserção do finder (ex.: `Expected exactly '1' node … (ContentDescription = 'Soltar da borda')`); verde: mesmo comando, 97 testes, 0 falhas; `*HudWindowPreferencesTest*` 22/22. Botão por fora da engrenagem (`HudNotchMeasure.kt`), parte própria `HudFloatPart` (sem valor novo em `HudNotchPart`); `HudNotch.kt` 741 linhas. Só alterna e grava o modo; o efeito na janela é a A08 |
| A07a | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingHole*"` com stub → 6 falhas de asserção (`HudFloatingHoleMathTest.kt:53` `esperado=90.0, obtido=0.0`); verde: mesmo comando, 10 testes, 0 falhas; `*HudOrbitGeometryTest*` 13/13. Inclinação é porcentagem (`tiltDegrees` → `tiltPercent`, fórmula do R2). Devolvida uma vez (identificadores em português) |
| A07b | feito | vermelho: `gradlew.bat desktopTest --tests "*HudOrbitField*"` com stub → falhas de asserção em `HudOrbitFieldTest.kt:36` (`assertCountEquals(7)`), `:45`, `:53`, `:63`, `:70`, `:93`; verde: mesmo comando, 6 testes, 0 falhas. Devolvida uma vez (fase lida na composição recompunha o campo a cada quadro → lida só no `offset { }`; anel sem `active`/`attention`/`refreshing`). Sem teste para recomposição nem para `active` (a semântica não expõe) |
| A08a | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingLayoutTest*"` com stub → 9 de 12 falhas de asserção (`HudFloatingLayoutTest.kt:30` `expected:<878.0.dp x 300.0.dp> but was:<0.0.dp x 0.0.dp>`); verde: mesmo comando, 12 testes, 0 falhas. Janela fixa com balão cabendo dos dois lados; `hitRect` só do que está visível |
| A08b | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingHud*"` com stub → 7 falhas de asserção (`HudFloatingHudTest.kt:54`… `:102`, nós ausentes); verde: mesmo comando, 9 testes, 0 falhas; `*HudOrbitField*` 6/6. Devolvida uma vez (balão sem moldura `HudBalloon`; engrenagem sem indicador de atualização → `badgeTone` + frase na descrição, `:96`/`:102` vermelhos antes). Sem teste: hover, balão à esquerda, arrasto no expandido |
| A08c | feito | vermelho: `*HudFloatingHostTest*` com stub → 11 falhas de asserção (linha literal não preservada pelo agente; registro do relatório); verde: `gradlew.bat desktopTest --tests "*ArchitectureRulesTest*" --tests "*HudFloating*" --tests "*HudOrbit*" --tests "com.usagemonitor.ui.HudNotchTest" --tests "*HudWindow*"` → 278 testes, 0 falhas (test-runner). Devolvida uma vez: `ArchitectureRulesTest.kt:507` `HudWindowHost: 382 linhas (limite 300)` → ramo de conteúdo em `HudWindowContent.kt`, geometria pura em `HudFloatingHost.kt`; `HudWindowHost` 295 linhas, `HudWindow.kt` 536. Janela real ainda não aberta |
| A08d | feito | defeito achado ao abrir o app (`gradlew.bat run`): `IllegalStateException: CompositionLocal LocalFontFamilyResolver not present` em `HudOrbitField.kt:169` ← `HudWindowHost` (escopo `application`), também no modo encaixado. Vermelho: `*HudOrbitGeometryTest*` → `HudOrbitGeometryTest.kt:164` `diametro completo 180.0 dp` (stub); verde: `--tests "*HudOrbit*" "*HudFloating*" "*ArchitectureRulesTest*" "*HudWindow*"` → 183 testes, 0 falhas. Diâmetro por estimativa de avanço Plex (`wordWidth`/`percentWidth`), igual à geometria encaixada |
| A08e1 | feito | teste real do dono (`prototipos/issue-619/r4-app-real-*.png`): campo pequeno (parâmetros ajustados no R2 em 2x) e rótulos sobrepostos com 8 contas. Vermelho: `*HudOrbitGeometryTest*` → `HudOrbitGeometryTest.kt:169` `uma caixa por conta expected:<8> but was:<0>`, `:31` `fieldD expected:<360.0> but was:<180.0>`; verde: `--tests "*HudOrbit*" "*HudFloating*" "*ArchitectureRulesTest*"` → 110 testes, 0 falhas. Padrões dobrados (decisão do dono), raio da órbita efetivo, caixas de rótulo sem cruzamento |
| A08e3 | feito | pedido do dono: fundo igual ao da barra HUD. Corpo `gargantuaHorizonBody(CircleShape)` + `HudStarfieldCanvas` com anéis, contas em execução e caixas de rótulo como o notch (`HudNotch.kt:315`); diâmetro calculado uma vez, slot por fase sem varredura. Vermelho: `HudOrbitGeometryTest.kt:195`, `:211` (paridade por fase, stub zero); verde: `--tests "*HudOrbit*" "*HudFloating*" "*ArchitectureRulesTest*"` → 113 testes, 0 falhas. Devolvida uma vez (céu sem poços nem zonas proibidas). Sem teste para a paridade do céu (desenho); conferência visual no app |
| A08e2 | feito | teste real do dono: o buraco não chegava aos cantos (a janela inteira, com vaga de balão, era presa à tela). Agora prende a âncora (buraco no minimizado, campo no expandido) e a janela pode passar da borda. Vermelho: `*HudFloating*` → 13 falhas de asserção (`HudFloatingHostTest.kt:162`, `:174`, `:186`; `HudFloatingPlacementTest.kt:38`…`:90`); verde: `--tests "*HudFloating*" "*HudWindow*" "*ArchitectureRulesTest*"` → 0 falhas (Host 23, Placement 9, Architecture 28). `HudWindowHost` 295 linhas |
| A08f1 | feito | segundo teste real (`r5-app-real-*.png`): ainda sem canto — o arrasto prendia a janela inteira (`HudWindow.kt:285-293` → `fitWindowPosition`). `hudDragStep` ganha `anchor` (o buraco); sem ele, igual a hoje. Vermelho: `HudWindowDragTest.kt:159` `expected:<(2728.0.dp, 1130.0.dp)> but was:<(2539.0.dp, 883.0.dp)>`; verde: `--tests "*HudWindowDrag*" "*HudFloating*" "*ArchitectureRulesTest*"` → 105 testes, 0 falhas |
| A08f2 | feito | pedido do dono: contas paradas, campo e texto menores; nomes saíam cortados (largura estimada sem `textSp`). Padrões `orbR 64`, `accD 48`, `markD 14`, `textSp 11`, `orbitPeriodSeconds 0` (contas fixas, sem relógio); largura = caracteres × 0,6 × `textSp` + 2 dp (avanço do Plex Mono). Vermelho: `HudOrbitGeometryTest.kt:32` `orbR expected:<64.0> but was:<80.0>`, `:303` `fase … período 0 Expected <0.0> actual <1.0>`; verde: `--tests "*HudOrbit*" "*HudFloating*" "*ArchitectureRulesTest*"` → 119 testes, 0 falhas |
| A10 | revertida | coluna da barra destacada (`15cb79af`, `00090630`, `287b5284`, `2cd09e8c`) regrediu no app real (`prototipos/issue-619/r7-regressao-*.png`): coluna espremida num quadrado de 64 dp e arrasto pior. Causa confirmada: a premissa "`sizes.collapsed` cresce até `expanded` ao abrir" era falsa — o corpo do notch usa `sizes.collapsed` aberto ou não (`HudNotch.kt:262-265`); trocar `collapsed` pelo buraco espremeu a coluna. Erro do orquestrador (premissa não conferida). `git revert` dos quatro commits; volta ao estado de `9f84ae90` |
| A11a | feito | decisão do dono: expandido = coluna vertical da barra ao lado do buraco, nada circular. `HudFloatingColumn` reusa `HudRingStrip(edge = LEFT)`, corpo `gargantuaHorizonBody(AppShapes.large)`, céu da barra e a íris de abertura (420/240 ms). Vermelho: `HudFloatingColumnTest.kt:31` `Expected '8' nodes but could not find any node … 'hud-ring-item'`; verde: `--tests "*HudFloatingColumn*" "com.usagemonitor.ui.HudNotchTest" "*ArchitectureRulesTest*"` → 2 + 97 + 28, 0 falhas |
| A11b | feito | geometria da coluna ao lado do buraco (lado com mais espaço, deslize vertical para caber na área, balão além da coluna, janela fixa 2·altura da coluna). Vermelho: `*HudFloatingLayoutTest*` → 11 falhas de asserção em 23 (`HudFloatingLayoutTest.kt:143`…`:297`); verde: `--tests "*HudFloating*" "*ArchitectureRulesTest*"` → 104, 0 falhas |
| A11c | feito | ligação da coluna (commit único: as partes só compilam juntas). `HudFloatingHud` troca o campo pela `HudFloatingColumn`, buraco sempre visível, alças junto da coluna, balão no hover alinhado ao anel em foco (`hudFloatingBalloonTop`, vermelho `HudFloatingLayoutTest.kt:267`); janela reserva as alças nas duas pontas (vermelho `HudFloatingLayoutTest.kt:355` `buttons … top=-22.0.dp`); host calcula pela coluna. Desvio: a parte pura do host (A11c-3) foi escrita antes do teste rodar (sem vermelho registrado). Falhas de teste de tela diagnosticadas por medição: raiz do teste 776×768 menor que a janela (defeito do teste) e hover lido antes do quadro seguinte (`waitForIdle`). Verde: `--tests "*HudFloating*" "*HudWindow*" "com.usagemonitor.ui.HudNotch*" "*ArchitectureRulesTest*"` → 294, 0 falhas (test-runner) |
| A11e | feito | pedido do dono: expansão "travada e rápida". Causa lida: a coluna só era composta expandida e a íris nasce aberta (`HudRetractedStrip.kt:201-210`), então surgia sem animação. Agora a coluna fica sempre composta em `columnSlot` com `revealed = expanded` (íris 420/240 ms da barra) e as alças entram/saem como as da barra. Vermelho: `HudFloatingLayoutTest.kt:293`, `:303`, `HudFloatingHudTest.kt:67`; verde: `--tests "*HudFloating*" "com.usagemonitor.ui.HudNotchTest" "*ArchitectureRulesTest*"` → 213, 0 falhas |
| A11f | feito | teste real (`r8-nao-esconde-*.png`): a coluna não recolhia com o ponteiro no vão. Causa lida: hover da janela inteira (`HudFloatingHud.kt:91-95`) com recorte de um retângulo só. Agora hover por parte (buraco, coluna, alças, balão), padrão `rememberHudHover` da barra. Vermelho: `HudFloatingHudTest.kt:108` e `:132` `expected:<false> but was:<true>`; verde: `--tests "*HudFloating*" "*ArchitectureRulesTest*"` → 119, 0 falhas |
| A11g | feito | teste real (`r9-sem-lista-minimizadas.png`): o buraco da coluna não abria a lista das minimizadas. Regras puras `hudFloatingHole*` (vermelho `HudFloatingHostTest.kt:111`…`:146`, 7 falhas) e balão `Hole` com `HudBlackHoleBalloonContent` da barra (vermelho `HudFloatingHudTest.kt:103`); verde: `--tests "*HudFloating*" "*HudWindow*" "com.usagemonitor.ui.HudNotchTest" "*ArchitectureRulesTest*"` → 303, 0 falhas |
| A11h | feito | teste real (`r10-corte-no-canto.png`): perto da base a pilha de alças saía da área útil. O deslize passa a considerar coluna + alças; alças embaixo se couberem na área, senão em cima. Vermelho: `HudFloatingLayoutTest.kt:451` `buttons y=1060 fora da área útil [-664.0, 416.0]`; verde: `--tests "*HudFloating*" "*ArchitectureRulesTest*"` → 132, 0 falhas |
| A11i | feito | teste real (`r11-balao-*.png`): balão de conta e de lista com a altura do balão da engrenagem (`HudWindowContent.kt:141`, `:143`) saía espremido/cortado; e o voo da conta para o buraco (confirmado pelo dono) não animava porque a coluna não recebia `departures`/`settle`/`minimizeProgress`. Agora balão de conta com a altura da geometria da barra e janela reservando a maior; coluna com o estado de saída da barra. Vermelho: `HudFloatingHudTest.kt:164` `Actual height is 176.0.dp, expected 200.0.dp` (depois corrigido para medir o corpo `HUD_BALLOON_TEST_TAG`); `HudFloatingColumnTest.kt:58` `a vizinha devia deslizar no meio da vaga: primeiro=456.0, meio=456.0, assentado=456.0` (com `departures` vazio); verde: `--tests "*HudFloating*" "*HudWindow*" "com.usagemonitor.ui.HudNotchTest" "*ArchitectureRulesTest*"` → 310, 0 falhas |
| A11j | feito | teste real (`r12-balao-*.png`): balão de conta esticado e conta aberta pela lista com botões achatados. Altura do corpo pela regra da barra (`HudNotch.kt:489-493`): conta → `hudBalloonHeight(conta, maior)`, lista do buraco → maior, engrenagem → a dela. Vermelho: `HudFloatingHudTest.kt:170` `Actual height is 400.0.dp, expected 138.0.dp`, `:175` `Actual height is 120.0.dp, expected 400.0.dp`; verde: `--tests "*HudFloating*" "*HudWindow*" "com.usagemonitor.ui.HudNotchTest" "*ArchitectureRulesTest*"` → 0 falhas |
| A11k | feito | teste real (`r13-balao-cortado-topo.png`): perto do topo o balão saía da tela (preso só à janela, `HudFloatingLayout.kt:259-262`). Agora preso também à área útil, com a altura do balão em uso. Vermelho: `HudFloatingLayoutTest` `topo do balão deve encostar no topo da área útil expected:<20.0.dp> but was:<0.0.dp>`, `base … expected:<80.0.dp> but was:<100.0.dp>`; verde: `--tests "*HudFloating*" "*ArchitectureRulesTest*"` → 0 falhas |
| A11l | feito | teste real (`r14-corte-*.png`): com todas minimizadas (coluna curta) e o buraco perto do topo, o fim do balão passava da base da janela. Janela agora com altura ≥ 2 × balão. Vermelho: `HudFloatingLayoutTest.kt:187` `base do balão 890.0.dp fora da janela 600.0.dp`, `:165`, `:154`; verde: `--tests "*HudFloating*" "*ArchitectureRulesTest*"` → 141, 0 falhas |

## Problemas em aberto e riscos

- Janela solta fora da borda muda a premissa da `hud-notch.md` (forma de encaixe, `hudHitRegionMode`, alfa no Linux);
  o custo depende da direção escolhida.
- Clique atravessável e transparência por proximidade (se escolhidos) são nativos por plataforma: medir antes de liberar.
- `HudNotch.kt` (721 linhas) e `HudNotchGeometry.kt` (735) estão perto do teto de 800: tudo do modo flutuante entra em
  arquivos novos; quem toca nesses dois começa extraindo.
- Nenhum teste exercita o sistema de janelas: A08 é aberto no app real no Windows antes do PR; Linux fica fora (A05).
