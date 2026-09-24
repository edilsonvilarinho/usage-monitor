# Profundidade, movimento e HUD em notch — execução

## Contexto

O app lia como "chapado" e o movimento, como "sem fluidez". As duas coisas eram regra escrita, não
acidente: o design system proibia sombra em card (`AppElevation.card = 0`), gradiente, translucidez e
animação contínua, e as quatro superfícies ficavam dentro de ~14% de luminância — o hover do card ia
de `#1B1818` a `#211E1E`, que ninguém enxerga. Não havia `spring()` em lugar nenhum: a barra de cota
saltava de largura e cor no quadro da coleta, o reordenar de cards encaixava seco, abas, segmentados,
menu e diálogo trocavam sem transição, e o `AnimatedContent` do Dashboard e do Histórico ignorava o
argumento da lambda e desenhava o estado atual nos dois slots. A HUD redimensionava a janela AWT a
cada quadro.

Referências visuais pesquisadas: **Codenotch** (notch escuro colado à borda, um anel por fornecedor,
hover desdobra um balão com barras cápsula e "Resets in…") e **ai-usagebar** (cards com barras
cápsula, marcador de ritmo, "Next update in 2m").

## Decisões do usuário

1. **Evoluir o design system**, não trocar de linguagem: Plex, paletas e primitivas ficam; as regras
   de profundidade e movimento são reescritas.
2. **HUD vira notch de borda com anéis.**
3. **Animação contínua permitida atrás de política**, desligada em testes e geradores.
4. **Janela da HUD transparente, com cantos e sombra desenhados pelo Compose**, sem acrílico nem JNA.

## Atividades

| # | Atividade | Estado |
|---|---|---|
| C1 | Tokens de motion, `AppMotionPolicy` e "Reduzir animações" | feito |
| C2 | Profundidade: `AppDepth`, `AppSurfaceLadder`, brilho e highlight | feito |
| C3 | Seleção animada: aba, segmentado, navegação lateral, chip | feito |
| C4 | Overlays que entram e saem: menu, tooltip, diálogo | feito |
| C5 | Estados de interação: foco, hover em camada, pressão | feito |
| C6 | Números animados e recarga do card | feito |
| C7 | `AppStateCrossfade` e o bug do `AnimatedContent` | feito |
| C8 | Expandir/recolher e banners | feito |
| C9 | Movimento da grade de cards | feito |
| C10 | Limpeza de movimento do `ApiUsageCard` | feito |
| C11 | Spike da janela transparente | feito — resultado B |
| C12 | Modelo puro da HUD | feito |
| C13 | HUD em janela própria | feito |
| C14 | Geometria de borda e migração da posição | feito |
| C15 | O notch com anéis | feito |
| C16 | Indicadores contínuos atrás da política | feito |
| C17 | Verificação final | feito (automática); manual pendente |

### Rodada 2 — identificação (pedido depois da entrega)

O usuário comparou com Codenotch e ai-usagebar e apontou o que faltava: **marca do fornecedor**,
**plano da conta** ("Max 20x", "ChatGPT Plus") e **o provedor sempre escrito** — a HUD mostrava só
"Padrão". O `ai-usagebar` foi clonado localmente como referência.

| # | Atividade | Estado |
|---|---|---|
| D1 | Marca do fornecedor (`AppProviderMark`) e adoção no card | feito |
| D2 | Plano da conta nos dados (`ApiUsageStats.planLabel`) | feito |
| D3 | HUD com provedor, marca e plano; plano no card; resumo na bandeja | feito |

### Rodada 3 — balão por anel, alças e ações (pedido depois da rodada 2)

O usuário comparou de novo com o Codenotch (`windows/codenotch/ui/notch.html`) e listou: hover num
anel abre **um balão só daquele provedor**, não o painel com todas as contas; cada cota com
**"87% usado · 13% restante"** e a **origem** ("Plus · via Codex"); os **botões do rodapé** e os
**do card** alcançáveis no modo barra; um **botão para mover** o notch; e o **timer cortado**
("04:5") na borda de cima. Decisões dele: as ações do rodapé moram num **balão aberto pela
engrenagem**, e **clicar num anel atualiza aquela conta**, como no Codenotch.

| # | Atividade | Estado |
|---|---|---|
| E1 | Timer cortado: folga de arredondamento de pixel na estimativa de texto | feito |
| E2 | Modelo do balão: usado/restante, origem, rótulo e grupo da cota | feito |
| E3 | Balão por anel com cauda, no lugar do painel de todas as contas | feito |
| E4 | Alças: mão (mover) e engrenagem | feito |
| E5 | Balão da engrenagem com as ações do rodapé (`AppShellActions`) | feito |
| E6 | Botões do card no balão (`cardActionsFor`) e clique no anel = atualizar | feito |
| E7 | Documentação, design system, protótipo, ajuda, capturas | pendente |
| E8 | Verificação | pendente |

## Pontos de situação

| Data | Atividade | Modelo | Comando | Resultado |
|---|---|---|---|---|
| 2026-09-24 | C1 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.theme.*" --tests "com.usagemonitor.ReducedMotionPreferencesTest" --tests "com.usagemonitor.ui.AppStatesTest" --tests "com.usagemonitor.ui.ComponentTest"` | Verde. Primeira passada teve 2 falhas nos testes novos de bitmap da barra: diferiam só os 4 pixels de canto do recorte arredondado, cujo alfa de antialiasing varia com o número de quadros compostos. O teste passou a ignorar os cantos; a largura do preenchimento não passa por eles. |
| 2026-09-24 | C2 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*"` + `gradlew.bat generateScreenshots` | Verde. A primeira captura saiu praticamente igual à anterior: sombra preta sobre `#131010` não aparece (sonda: 10dp escurecem o fundo em 3/255) e o brilho, desenhado por baixo do conteúdo, era coberto pelo fundo do cabeçalho do card. Brilho passou para cima do conteúdo, com teto de 56dp, e a borda ganhou gradiente claro no topo. |
| 2026-09-24 | C3 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `o sublinhado desliza ate a aba escolhida` (indicador termina com `left` e largura iguais aos da aba nova). |
| 2026-09-24 | C4 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `o menu some da arvore depois da saida` e os testes de "abre para cima" do `FooterBarTest`. A saída da janela de diálogo não tem teste de componente (a moldura exige `WindowScope`); fica para a verificação manual do C17. |
| 2026-09-24 | C5 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `o campo focado desenha o anel de foco` (pixels 0 e 1 da borda esquerda mudam com o foco). |
| 2026-09-24 | C6 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `o numero animado termina com um so no no valor novo` e `AppAnimatedNumberTest`. |
| 2026-09-24 | C7 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `a troca de estado mantem o estado antigo no slot que sai` (relógio manual, meio da saída: os dois textos presentes; depois do idle, só o novo). |
| 2026-09-24 | C8 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` | Verde, incluindo `o bloco recolhido sai da arvore depois de fechar`. |
| 2026-09-24 | C9 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` + `gradlew.bat generateScreenshots` | Verde depois de corrigir o fixture do teste novo (alvo não-Anthropic não leva perfil). A captura do dashboard saiu idêntica à anterior pixel a pixel: a primeira colocação é salto, sem animação. |
| 2026-09-24 | C10 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*"` + `gradlew.bat generateScreenshots` | Verde. As capturas mudaram em no máximo 5/255 por pixel (ruído subpixel), sem diferença visível; não foram commitadas agora e voltam no C17. O aquecimento de 20 × 100ms do gerador cobre as molas `GENTLE` (~450ms). |
| 2026-09-24 | C11 | Claude Opus 5.5 | teste descartável `TransparentWindowSpikeTest` via `gradlew.bat desktopTest --tests "com.usagemonitor.spike.*"`, com `skiko.renderApi` padrão, `SOFTWARE` e `OPENGL` | Resultado **B** nos três: clique em pixel alfa 0 de uma `ComposeWindow` transparente é **engolido** — não chega nem ao conteúdo Compose nem à janela de trás. Clique de controle fora do overlay chega à janela de trás; clique no centro opaco chega ao Compose. O arquivo do spike não foi commitado. |
| 2026-09-24 | C12 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HudModelTest" --tests "com.usagemonitor.ui.*"` | Verde: sete casos do modelo e a suíte de UI inteira (a barra HUD atual consome o modelo por um adaptador, sem mudança visual). |
| 2026-09-24 | C13 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.Hud*" --tests "com.usagemonitor.presentation.*"` | Verde. Os testes de `HudBar` já montavam a barra direto, sem `DesktopWindowFrame(hud)`, e não precisaram mudar. A verificação da janela real fica para o C17. |
| 2026-09-24 | C14 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.HudWindowPreferencesTest"` | Verde: dez casos de geometria e quatro de posição (padrão, ida e volta, migração da pílula antiga, meia gravação ignorada). |
| 2026-09-24 | C15 | Claude Opus 5.5 | `gradlew.bat allTests` + renderização descartável do notch (topo e lateral, escuro e claro, parado e aberto) | Verde: 2077 testes, 0 falhas. A primeira renderização mostrou a trilha dos anéis branca opaca — o alfa da camada de pressão tinha sido sobrescrito com 1 —; corrigida para a camada com 1,6× o peso. |
| 2026-09-24 | C16 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.presentation.SessionPulse*" --tests "com.usagemonitor.ui.ComponentTest"` | Verde. O teste de giro falhou uma vez no caso estático: sobre fundo transparente dois instantes parados diferiam pelo antialiasing acumulado; com fundo opaco, parado é idêntico e com a política contínua os dois instantes diferem. |
| 2026-09-24 | C17 | Claude Opus 5.5 | `gradlew.bat allTests` + `gradlew.bat generateScreenshots` + `gradlew.bat generateHelpMedia` + `desktopTest --tests "*HelpMediaResourcesTest*"` | Verde: 2079 testes, 0 falhas; capturas do README e as doze demos da ajuda regeneradas (a de modos de janela já mostra o notch aberto). `gradlew.bat run` **não** foi executado: a versão instalada estava aberta e o `SingleInstanceGuard` só a traria para frente. |
| 2026-09-24 | D1 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.AppProviderMarkTest" --tests "com.usagemonitor.ui.ComponentTest"` + `gradlew.bat generateScreenshots` | Verde; a captura mostra asterisco, nó e marca da DeepSeek no acento de cada card. |
| 2026-09-24 | D2 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.domain.AccountPlanLabelTest"` | Verde: mapeamento dos três fornecedores, `planLabel` no Codex, no Cursor, no repositório da Anthropic e na ida e volta do cache. |
| 2026-09-24 | D3 | Claude Opus 5.5 | `gradlew.bat allTests` + `generateScreenshots` + `generateHelpMedia` + renderização descartável do notch | Verde: 2088 testes, 0 falhas. Duas correções vindas do olho: no painel, dois `weight` na mesma linha dividiam a sobra e truncavam "Anthropic —…"; no card, o selo do plano ia parar longe do nome porque a coluna mede o e-mail. |
| 2026-09-24 | E1 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTextFitTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.HudNotchGeometryTest"` | Primeira passada do teste novo **vermelha**, reproduzindo o defeito: em toda escala fracionária (105%–200%) o texto desenhado passava a estimativa em até 0,8dp — o Skia arredonda a largura da linha para cima em pixel inteiro. Na faixa as diferenças somavam e a contagem, último item, quebrava. Com 1dp de folga por texto: verde. |
| 2026-09-24 | E2 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HudModelTest"` | Verde, com cinco casos novos: usado/restante nos dois idiomas, "<1" nas duas pontas, saldo e atividade observada sem a linha, rodapé de plano + origem, grupo e título do Antigravity. |
| 2026-09-24 | E3 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.Hud*" --tests "com.usagemonitor.ui.Hud*" --tests "com.usagemonitor.presentation.Hud*"` | Verde: 19 casos do notch (balão só da conta do anel sob o ponteiro; costura de tamanho do notch e do balão nas 4 bordas) e os novos de geometria (notch parado na tela ao abrir em 5 frações × 4 bordas, balão da conta mais alta, grupos). Renderização descartável nas 4 bordas: cauda no anel certo e a contagem inteira ("04:50"). |
| 2026-09-24 | E4 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.Hud*" --tests "com.usagemonitor.ui.Hud*"` | Verde: 24 casos do notch (alças só abertas, arrasto pela mão sem abrir nada, mão presente durante o arrasto, engrenagem, hover na alça conta como no notch, alças dentro da janela aberta nas 4 bordas) e 13 de geometria (notch parado na tela e alças dentro da janela até na fração 0 e 1). A renderização em fração 0,9 reprovou a primeira versão: engrenagem fora da janela. |
| 2026-09-24 | E5 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.Hud*" --tests "com.usagemonitor.ui.Hud*" --tests "com.usagemonitor.ui.FooterBar*" --tests "com.usagemonitor.ui.ComponentTest"` | Verde: 28 casos do notch (engrenagem abre e fecha o balão; ações do rodapé pelas mesmas descrições; modos com o corrente marcado; anel troca para a conta; altura do balão com e sem atualização) e as suítes do rodapé e de componentes sem mudança. Renderização descartável: cauda na engrenagem nas bordas de cima e da direita. |
| 2026-09-24 | E6 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.Hud*" --tests "com.usagemonitor.ui.Hud*" --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.ComponentTest" --tests "com.usagemonitor.ui.*Card*"` | Verde: 30 casos do notch (clique no anel atualiza só aquela conta, fora dos anéis nada; ação declarada na semântica do anel; botões do card no balão; indicador de atualização sem clique próprio), `CardActionsTest` (3) e o `ComponentTest` (99) sem mudança na barra do card. Renderização descartável: fileira histórico · sessões · time · presença · atualizar no balão. |

## C1 · Tokens de motion e política

- `AppMotion.Springs` (`GENTLE` 1.0/400, `SNAPPY` 1.0/1500, `EXPRESSIVE` 0.75/600), `exit = 90`,
  `emphasizedEasing`. `AppMotionPolicy` (`Static`, `Live`, `Reduced`) e `LocalAppMotionPolicy`.
- `appSpringSpec`/`appTweenSpec` são funções puras; `appSpring`/`appTween` as leem da composição.
  Com `reduced` as duas viram `snap()`.
- `AppTheme(motion = AppMotionPolicy.Static)` por default. O `Main` passa
  `AppMotionPolicy.forPreference(reducedMotion)` às oito chamadas de `AppTheme` dele e às janelas de
  Ajuda e Novidades.
- Primeiros consumidores: `AppProgressTrack` (largura por `GENTLE`, cor por tween) e `AppSwitch`
  (botão por `SNAPPY`; trilho, borda e botão mudam de cor juntos).
- **Por que ignorar os cantos no teste de bitmap.** A cena que animou compõe mais quadros que a que
  nasceu parada, e o antialiasing do recorte arredondado acumula alfa diferente nos quatro pixels de
  canto. O que o teste afirma é a largura do preenchimento; comparar os cantos testaria o compositor.

## C2 · Profundidade

- `AppDepth` (`FLAT`, `CARD` 1/6dp, `RAISED` 2/10, `OVERLAY` 3/14, `DIALOG` 4/20) substitui
  `AppElevation`. `appDepth` empilha duas `shadow` com `clip = false` — o Compose 1.7 não tem
  `dropShadow` com deslocamento e desfoque separados.
- `AppSurfaceLadder.of(preset)`: camadas de hover (6%/4,5%) e pressão (10%/8%) do `foreground`,
  highlight, brilho, cor e alfa de sombra e as duas pontas da borda. Derivado, sem tocar nos hex.
- `appSurfaceBlock(depth, sheen)`: sombra antes do recorte; com `sheen` o bloco ganha brilho e a
  borda em gradiente. `AppDataSurface`/`AppDataSurfaceFlush` passam a `CARD` + brilho.
- Card: `CARD` em repouso, `RAISED` + 1dp com hover, `DIALOG` arrastado, `FLAT` como alvo do
  arrasto, tudo por mola `GENTLE`. O fundo não troca mais no hover.
- `AppMenu` em `OVERLAY`; `AppTooltipSurface` em `RAISED` pela sombra do sistema (a do Material saiu,
  o `tonalElevation` de 2dp ficou para não mudar o tom da bolha).
- Diálogos não entram: são janelas do SO, com a sombra do SO.

## C3 · Seleção animada

- `AppSlidingIndicator.kt`: cada opção publica `(início, tamanho)` com `onPlaced`/`positionInParent`
  e o contêiner desenha **um** indicador, animado por mola `SNAPPY`. A primeira posição é salto.
- `AppTabs`: sublinhado num `Box` que embrulha só a fileira (mesma origem das posições), com
  `APP_TABS_INDICATOR_TEST_TAG` para o teste medir onde ele parou.
- `AppSegmentedControl`: polegar atrás dos rótulos, divisores por cima.
- `AppSettingsNav`: bloco vertical atrás da coluna de itens.
- `AppToggleChip` e os rótulos das três primitivas: cor por tween de 180ms.

## C4 · Overlays que entram e saem

- `AppMenu`: `MutableTransitionState` + `rememberTransition`; o `Popup` fica composto até a saída
  terminar. Entrada por escala 0,96 → 1 (`EXPRESSIVE`) e fade; saída por fade de 90ms. A origem da
  escala é a borda que encosta na âncora — `AppMenuPositionProvider.opensUpward`, campo comum lido no
  desenho do mesmo quadro.
- `AppTooltip` fica como está: o `TooltipBox` do Material já entra e sai com fade.
- `DesktopDialogFrame`: o botão de fechar esmaece a **janela** AWT em 140ms e só então pede o
  fechamento, restaurando a opacidade depois. Sem translucidez de janela ou com movimento reduzido,
  fecha na hora. Fechar pelo sistema (Alt+F4) continua imediato.

## C5 · Estados de interação

- `AppButton`/`AppIconButton`: repouso → hover → pressão (`pressedLayer` sobre o hover), por tween.
  Sem ripple (`indication = null`). Só o de ícone encolhe (`appPressScale`, 0,96, `SNAPPY`).
- `CardIconActionButton`: ganhou hover, pressão e escala — era o único botão sem resposta ao ponteiro.
- `AppDataRow`: hover e pressão como camadas somadas; a seleção continua em `surfaceVariant`.
- `AppTextField`/`AppTextArea`: anel de foco de 2dp em `--info`, cor e largura em tween.

## C6 · Números e recarga

- `AppAnimatedNumber`: `AnimatedContent` por texto; o valor novo entra de baixo quando sobe e de cima
  quando desce (`numericDirection`, puro e testado), mola `GENTLE`, `SizeTransform` com recorte.
  Adotado no percentual das cotas do card e no valor de `AppMetricBlock`.
- Recarga do card: `RefreshGlyph` no lugar do `CircularProgressIndicator`. Gira só com
  `AppMotionPolicy.continuous`; parado, fica no tom de informação e a semântica diz "Atualizando…".
- `ShimmerBox.kt` apagado: sem chamador, e com o deslocamento do gradiente em pixels.

## C7 · Troca de estado

- `AppStateCrossfade(state, key)`: `AnimatedContent` com `contentKey` e o conteúdo recebendo o estado
  **só** pelo parâmetro. Fade + subida de 1/24 da altura (mola `GENTLE`), saída em fade de 120ms.
- O defeito: `DashboardScreen` e `HistoryScreen` faziam `{ _ -> when (val state = uiState) ... }`, e
  os dois slots desenhavam o estado novo durante a transição.
- Adotado em Dashboard, Histórico, Sessões CLI, Uso do time, Presença, Chaves do time e na troca de
  aba das Configurações. Em Sessões CLI e Uso do time a chave separa lista de detalhe, então abrir
  uma sessão também faz a transição. `Success → Success` do laço ao vivo continua sem animação.
- Codex CLI ficou de fora: tem dois `when` sobre o mesmo estado e a troca dele é de uma área só.

## C8 · Expandir, recolher e faixas

- `AppExpandable`: `AnimatedVisibility` que cresce de cima (`GENTLE`) com fade e recolhe em tween;
  fechado, o conteúdo sai da composição como no `if (expanded)` de antes. Adotado no "Avançado" e no
  glossário de Sessões CLI e na edição de perfil das Configurações → Contas.
- `appItemMotion()`: `animateItem` com os tokens do sistema nos itens com chave das listas de time,
  presença e sessões — abrir um integrante desliza as linhas de baixo em vez de empurrá-las.
- Faixa de atualização do dashboard entra e sai por `AppExpandable`, desenhando o último estado não
  nulo (`rememberLatestNonNull`) durante a saída.
- `SnackbarHost` do dashboard virou sobreposição embaixo: dentro da coluna ele empurrava a grade ao
  aparecer e a puxava ao sumir.
- Os banners de erro por alvo continuam sem animação: saem de uma coleta e ficam; animá-los a cada
  recomposição da lista de avisos não descreveria mudança nenhuma.

## C9 · Grade de cards

- Posicionamento animado dentro do próprio `Layout` (`placeAnimated`): cada card tem um
  `Animatable<IntOffset>`; a primeira colocação é salto e as seguintes deslizam pela mola `GENTLE`.
  Cobre reordenar, minimizar um vizinho e a troca de uma para duas colunas.
- Durante o arrasto a grade é disposta na **ordem de prévia** (`previewCardOrder`, pura e testada):
  os vizinhos abrem o vão enquanto o card está no ar, e soltar não move mais nada.
- As caixas usadas para achar o alvo são **congeladas** no início do arrasto; medir contra caixas que
  andam com a prévia faria o alvo trocar a cada quadro.
- O card arrastado é posto em `início congelado + deslocamento`; ao soltar, a posição do ponteiro vira
  a partida da mola (`snapTo` com `CoroutineStart.UNDISPATCHED`, antes de limpar o arrasto).
- `isDragTarget` deixou de ser passado: o vão aberto já diz onde o card cai.
- Os filhos são identificados por `layoutId`, não pela ordem de composição — a prévia reordena o
  layout sem recompor os cards.

## C10 · Card

- Entrada: fade por tween enfático de 240ms, subida e escala por mola `GENTLE`, tudo pela política
  (com "Reduzir animações" o card nasce no lugar). `CardAnimations` saiu, com os comentários que
  diziam 600ms e 250ms onde o código dava 420ms e 180ms.
- Um dono só do tamanho: o `animateContentSize` do card inteiro saiu, e o `SizeTransform` do minimizar
  anda pela mesma mola. As duas animações aninhadas esticavam o card em dois tempos.

## C11 · Spike da janela transparente

**Pergunta:** numa `Window(transparent = true)` do Compose Desktop no Windows 11, clique sobre pixel
totalmente transparente atravessa para a janela de trás?

**Montagem:** um `Frame` AWT com contador de cliques atrás; por cima, `ComposeWindow` sem decoração,
transparente, sempre no topo, 300×300, com um quadrado opaco de 100dp no centro (sombra de 12dp).
`java.awt.Robot` clica em três pontos: fora do overlay (controle), no canto transparente do overlay e
no centro opaco.

**Duas rodadas descartadas antes da medida válida.** Na primeira o `Frame` ficou atrás da janela do
terminal e nem o clique de controle chegava; na segunda, com os dois "sempre no topo", o `Frame`
passou para cima do overlay. A montagem final traz o `Frame` à frente e só o overlay fica no topo.

**Resultado (três render APIs, duas rodadas na padrão):**

| Clique | Janela de trás | Conteúdo Compose |
|---|---|---|
| Controle, fora do overlay | recebeu | — |
| Pixel transparente do overlay | **não recebeu** | **não recebeu** |
| Centro opaco | — | recebeu |

**Decisão: resultado B.** A janela da HUD não pode ficar do tamanho do painel expandido o tempo todo:
a área transparente engoliria o clique de quem está atrás, que é justamente a queixa que fez a
largura da pílula virar teto. Portanto:

- **Em repouso** a janela tem o tamanho do notch **mais a margem da sombra** — é a única área que
  captura clique.
- **Ao entrar o ponteiro**, a janela cresce **de uma vez** para o tamanho do painel expandido (a área
  nova é transparente, então o salto não se vê) e a mola roda **dentro** dela.
- **Ao sair**, o conteúdo recolhe pela mola e só **depois** de assentar a janela encolhe.
- Nenhum redimensionamento AWT por quadro — era essa a fonte do tranco da HUD anterior.

Ficaram fora da medida, para a verificação manual do C17: combinação com `applyWindowOpacity`, halo
na borda da sombra e CPU da rotação contínua.

## C12 · Modelo puro da HUD

- `HudModel.kt` (`commonMain`): `buildHudAccounts(quotaRisks, cardOrder, language, now, activeTargets)`
  → `List<HudAccount>`. Uma conta por alvo na ordem dos cards; palavra e tom da pior cota; todas as
  cotas como `HudQuota` (rótulo curto, percentual do card, fração presa a 0..1, tom, reset, se há
  projeção); `rings` limitados a `MAX_HUD_RINGS` = 3; `focusIndex` na cota de pior risco, e no
  empate na de maior percentual.
- `main()` perdeu o bloco que montava as linhas; a barra atual recebe `HudAccount.toSourceStatus()`,
  adaptador que some quando o notch a substituir.

## C13 · HUD em janela própria

- `HudWindowHost` (`desktopMain/HudWindow.kt`): coleta os fluxos, monta as contas com
  `buildHudAccounts`, guarda hover/arrasto/âncora e abre uma `Window` sem decoração, sempre no topo,
  sem redimensionamento pelo usuário, com `AppTheme(motion)` e a opacidade do usuário.
- `main()` perdeu ~250 linhas: estado, geometria, efeitos e composição da HUD, o guard do coletor de
  persistência, o piso de tamanho da HUD e os termos HUD de `alwaysOnTop`/`resizable`. A janela
  principal passou a `visible = !hudMode`.
- `DesktopWindowFrame` perdeu `hud`/`hudContent`.
- Corrigido no caminho: o arrasto limitava a posição à área **útil** e o encaixe usava a tela
  **inteira**; agora os dois usam a tela inteira, e a barra chega sobre a barra de tarefas também
  durante o arrasto.
- `restoreMainWindow` sai da HUD antes de ativar a janela principal.

## C14 · Geometria de borda

- `HudNotchGeometry.kt`: `HudEdge` (enum novo), `hudNotchSizes` (recolhido e aberto, com os ombros),
  `hudWindowBounds` (janela colada à borda, margem de sombra só nos três lados de dentro, presa à tela
  perto dos cantos, com o centro do notch em coordenadas da janela) e `nearestHudPlacement`.
- **A geometria é dona do tamanho do conteúdo**: o notch composto usará estes números como tamanho do
  contêiner, sem medir nada — medir e devolver para a janela fecharia o laço de redimensionamento.
- A largura recolhida é o maior entre percentual e palavra, e por isso uma coleta que troca `9%` por
  `88%` não muda a janela.
- `HudPlacement(borda, fração)` gravado em `hudEdge`/`hudEdgeOffset`; a posição da pílula antiga
  (`hudWindowX/Y`) migra uma vez para a borda mais próxima e as chaves velhas são apagadas. Estreia no
  topo em 82%, onde a pílula nascia, e não no centro, onde fica o título de janela maximizada.

## C15 · O notch

- `AppUsageRing` (`commonMain`, substitui o `UsageArcChart` sem uso, que tinha peso 700 e brilho de
  acento): arcos concêntricos por mola `GENTLE`, trilha tracejada sem projeção, arco de sessão ativa e
  pulso de atenção só com `AppMotionPolicy.continuous`, frase inteira na semântica.
- `HudNotch` (`desktopMain`): forma com ombros côncavos, anéis + percentual em foco + palavra parado,
  painel por conta aberto, contagem e atualização uma vez no fim, `hudPressGesture` preservado. O
  contêiner usa os tamanhos de `hudNotchSizes`.
- `HudWindowHost` reescrito: janela transparente; em repouso do tamanho do notch, cresce de uma vez ao
  entrar o ponteiro e encolhe depois de a mola assentar (resultado B do C11); arrasto livre e encaixe
  por `nearestHudPlacement`, gravado como borda + fração.
- Apagados: `HudBar`, `HudSourceStatus`, `HudQuotaChip`, `HudPanelRow`, o adaptador `toSourceStatus`,
  `HudWindowGeometry.kt` inteiro e os testes `HudBarHeightTest`, `HudBarCountdownTest`,
  `HudBarUpdateIndicatorTest`, `HudWindowGeometryTest` e os casos de HUD do `DesktopWindowFrameTest`.
  As asserções deles migraram para `HudNotchTest`, mais a costura de tamanho nas quatro bordas.
- Textos: descrição do interruptor nas Configurações e o tópico "Modos de janela" da ajuda (PT/EN)
  deixaram de falar em "faixa fina no topo". O rótulo "Barra HUD" continua — ele é citado pelos passos
  da ajuda e pelo menu de modos.
- Design system: `AppHudBar` (contrato, `.d.ts`, `.jsx`) reescrito como notch, `AppUsageRing` novo,
  kit `Hud.jsx`, índice e regra de transparência do readme; seção "Barra HUD — notch" do protótipo.

## C16 · Indicadores contínuos

- `GetActiveCliSessionPulsesUseCase.activity()` devolve pulsos **e** contas com turno na janela, da
  mesma leitura; `invoke()` continua devolvendo só os pulsos.
- `SessionPulseViewModel.activeTargets` publica as contas ativas; `HudWindowHost` o repassa a
  `buildHudAccounts`, e o anel da conta ganha o arco fino que gira.
- `rememberSessionPulseFrame` passou para trás de `AppMotionPolicy.continuous`: sem ela o botão fica
  aceso e parado no pico da primeira severidade.
- Custo de CPU da rotação contínua fica para a verificação manual do C17.

## C17 · Verificação

**Automática (feita):** suíte completa, capturas e demos regeneradas e commitadas.

**Manual (pendente — precisa da janela real, com a versão instalada fechada):**

- `gradlew.bat run` a 100% e 115%, em Obsidiana, Porcelana e um preset colorido: hover e arrasto de
  card com o vão abrindo, menu e diálogo com saída, aba deslizando, barra e percentual animando numa
  coleta.
- HUD nas quatro bordas: arrastar, soltar perto de cada borda, reiniciar (posição gravada e a
  migração da pílula antiga), clique, botão direito, `Ctrl+Shift+H`, item da bandeja, e a janela
  crescendo sem piscar ao entrar o ponteiro.
- Opacidade da janela combinada com a janela transparente da HUD, e halo na borda da sombra.
- "Reduzir animações" ligado e desligado; CPU com o arco de sessão ativa girando (meta < ~1%).

## D2 · Plano da conta

- `ApiUsageStats.planLabel` (campo novo com default; fontes sem plano continuam iguais) e
  `AccountPlanLabel.kt`, dono único da tradução, com o mesmo mapeamento do ai-usagebar:
  - Claude: `subscriptionType` + multiplicação do `rateLimitTier` do `.credentials.json` → "Max 20x",
    "Pro". Não vem do endpoint de uso; `AnthropicSession` passou a carregar os dois campos.
  - Codex: `plan_type` da própria resposta de uso → "ChatGPT Plus".
  - Cursor: `membershipType` do resumo de uso → "Ultra".
- Sem o campo não há rótulo — nunca "Desconhecido", que afirmaria um plano estranho onde só falta
  o dado.
- O cache do dashboard guarda o plano; sem isso o card aberto pelo cache ficaria sem ele até a
  primeira coleta.
- Antigravity e Gemini ficam sem plano: o `agy /usage` e os logs do Gemini CLI não o informam, e o
  caminho do ai-usagebar (RPC local do language server, OAuth do Google) é outra integração.

## D3 · Identificação na tela

- `ApiUsageStats.displayTitle()` é o dono único do título ("Anthropic — Padrão"): card e HUD usam o
  mesmo. `HudAccount` ganhou `source` e `planLabel`.
- HUD: marca no miolo do anel (cor do texto; tamanho calculado pelo miolo livre dos arcos), bloco
  aberto com marca no acento, título completo, plano em tom secundário e estado à direita. Anel de 28
  para 36dp.
- Card: selo do plano na linha do título (`API_USAGE_CARD_PLAN_TAG`).
- Bandeja: `hudTraySummary` no tooltip, com corte em 127 caracteres.

## E1 · Timer cortado

- **Causa medida, não suposta** (`HudNotchTextFitTest`): a geometria estima a largura pelo avanço
  da Plex Mono, e o `TextMeasurer` devolve a linha arredondada para cima em pixel inteiro. Em
  densidade 1 — a dos testes de componente — as duas contas batem, e por isso nenhum teste pegou;
  com 115% sobre 125% do Windows cada texto sai até ~0,8dp mais largo. A faixa soma as diferenças e
  o `Row` entrega ao último filho, a contagem, o que sobrou: com `maxLines = 1` o texto quebra por
  caractere e aparece "04:5".
- Correção em `charWidth`: 1dp de folga por texto, que cobre um pixel em qualquer densidade ≥ 1.
  O teste varre 100%–200% de 5 em 5 mais 144% e 172%, as combinações comuns com a escala do Windows.

## E2 · Modelo do balão

- `HudQuota` ganhou `title` (o título do bloco expandido do card, `expandedQuotaTitle`, sem o prefixo
  do grupo), `group` (Antigravity/Cursor, pelos donos dos rótulos) e `usedLeftText`.
- **Usado truncado, restante derivado do usado exibido**: o balão não pode dizer 88% ao lado do
  anel que diz 87%, e os dois números somam 100. Abaixo de 1% a linha diz "<1%" — truncar daria
  "0% usado" com consumo real —, e perto do teto, "<1% restante". Saldo e atividade observada
  não têm teto e ficam sem a linha.
- `HudAccount.originLabel` por `hudSourceOrigin`, `when` exaustivo sobre `ApiSource`: descreve o
  caminho da leitura ("via Codex", "via Antigravity CLI", "via chave de API"), não a empresa, que
  já está no título. `detailLine` junta plano e origem: "Plus · via Codex".

## E3 · Balão por anel

- **O notch deixa de crescer.** O painel de todas as contas (`HudPanel`/`HudQuotaRow`) saiu. Com o
  ponteiro em cima, o balão de **uma** conta — a do anel sob o ponteiro — aparece ao lado do notch,
  do lado de dentro da tela, como o `#card` do Codenotch. Passar para outro anel desliza o balão pela
  mola `GENTLE` e troca o conteúdo por `AppStateCrossfade`; a entrada é escala 0,96 → 1
  (`EXPRESSIVE`) a partir do lado do notch, e a saída, fade de 90ms.
- `HudBalloon.kt`: corpo em `OVERLAY` com brilho e borda de luz, e a **cauda** — a cunha curva do
  `TooltipTail` do Codenotch, com os mesmos pontos de controle normalizados — desenhada depois do
  corpo, cobrindo o trecho da borda onde encosta. A ponta toca o notch e aponta para o centro do anel;
  a posição dela é lambda lida no desenho, e o balão deslizando não recompõe o conteúdo.
- Conteúdo: marca no acento, título e estado; por cota, título do card ("Sessão 5h") e
  "Reinicia 22h59", barra e "68% usado · 32% restante"; cotas do mesmo grupo numa caixa sob o nome
  dele; rodapé "Max 20x · via Claude Code". Toda linha tem altura fixa.
- **Geometria**: `hudBalloonHeight` soma as mesmas linhas que o balão compõe, e `hudNotchSizes`
  reserva o balão da conta **mais alta** — trocar de anel não redimensiona a janela.
  `hudOpenWindowBounds` calcula a janela aberta com o notch **no mesmo ponto da tela** em que estava
  parado; perto do canto quem se ajusta é o balão, preso dentro da janela.
- O contêiner do notch passou a ocupar a janela inteira e a posicionar o notch pelo centro que a
  geometria dá; `dockedTo` saiu do host. A janela encolhe 200ms depois de o ponteiro sair (era 450ms,
  o tempo da mola do notch crescendo, que não existe mais).
- Hover é a **união** do corpo do notch e do balão: o caminho do anel até o balão passa pela cauda,
  que é opaca e do balão — pixel transparente não recebe evento (C11).
- Teste de costura reescrito: nas quatro bordas o notch tem o tamanho recolhido parado e aberto, o
  balão de cada conta tem a caixa de `hudBalloonBoxSize`, cabe inteiro na janela aberta, e a coluna
  de linhas mede exatamente `hudBalloonHeight` menos o padding.

## E4 · Alças: mão e engrenagem

- `HudHandles.kt`: o `MoveHandle` e o `SettingsOrb` do Codenotch. A **mão** fica na ponta de perto
  (em cima ou à esquerda) e a **engrenagem** na de longe, cada uma um disco de 32dp em `RAISED`,
  centrado na espessura do notch e 6dp além da ponta. Entram com o notch aberto (fade e escala 0,86 → 1
  pela mola `EXPRESSIVE`); o hover acende o glifo e a pressão encolhe o disco.
- **A mão move**: é o mesmo `hudPressGesture` do corpo, com o clique vazio, e o host continua lendo o
  ponteiro na tela. Carregando, a mão **fica na composição** — tirá-la cancelaria o gesto no meio — e
  ganha a borda de informação de 2dp. O arrasto pelo corpo do notch continua.
- **A engrenagem abre as Configurações** neste passo, como o orbe do Codenotch; o E5 a troca pelo balão
  de ações. `HudWindowHost` ganhou `onOpenSettings`.
- Parado, cada alça é um **arco de um quarto** (`HudHandleHint`) rente à borda da tela, dentro da
  margem de sombra de 16dp que a janela recolhida já tem: nenhuma área nova engolindo clique. Na cor
  `outline` — a borda de luz do notch sumia contra fundo escuro, e a renderização mostrou isso.
- O hover do notch passou a ser a união de corpo, balão **e alças**.
- **Geometria**: `HudNotchSizes.withHandles` (o notch mais as duas alças). A área aberta reserva as
  alças ao longo da borda, e durante o arrasto a janela tem o tamanho `withHandles` — simétrico, então o
  centro da janela continua sendo o do notch, que é o que `nearestHudPlacement` lê.
- **O centro do notch é preso pela reserva com as alças, parado e aberto** (`reserveAlong` em
  `hudWindowBounds`, `hudRestWindowBounds`). A primeira renderização em fração 0,9 mostrou a
  engrenagem fora da janela: o centro era preso contando só o notch. Com a mesma reserva nos dois
  estados o notch continua sem andar ao abrir, e as alças cabem na tela até no canto — o preço é o notch
  parar 38dp mais longe do canto que antes.

## E5 · Balão da engrenagem

- **A engrenagem abre e fecha um balão com o que o rodapé do modo padrão oferece** — a barra HUD não
  tem rodapé. Título "Usage Monitor" com a contagem até a próxima coleta; os três modos de janela; a
  fileira de ações; a atualização pendente, quando há. A cauda aponta para a engrenagem; passar por um
  anel troca para o balão daquela conta.
- **A fileira é o próprio `FooterActionGroup`**, que passou a `internal`: mesmos ícones, mesmas
  descrições, mesmas condições de admin. Duas cópias divergiriam na primeira tradução.
- **Os modos vão em linhas, não no menu do rodapé**: aquele é `Popup`, e popup no Compose Desktop é
  recortado pela própria janela — o motivo registrado no KDoc do `WindowModeMenuButton` desde a
  barra antiga. Linha com a marca do corrente num espaço reservado em todas, a regra do `AppMenu`.
- **`AppShellActions`** (`desktopMain`): atualizar, configurações, ajuda, modo de janela, exportação e
  as duas visões de admin, montadas **uma vez** em `main()` e consumidas pelo `DashboardScreen` e
  pelo `HudWindowHost`. Os lambdas saíram do meio da chamada do `DashboardScreen`; o `onWindowModeChange`
  saiu de dentro do conteúdo da janela principal, onde a HUD não o alcançava. O breadcrumb da ajuda pelo
  F1 da HUD virou o mesmo "Ajuda" do rodapé.
- Na HUD a exportação lê o `UiState.Success` corrente e não mostra snackbar: o diálogo de arquivo é o
  retorno, e falha vai ao mesmo `recordFailure`.
- Geometria: `hudAppBalloonHeight` soma as linhas do balão, e o balão reservado é o maior entre as
  contas **e** a engrenagem. Sem conta nenhuma ele continua existindo: é a saída do modo.

## E6 · Botões do card no balão e clique no anel

- **`cardActionsFor` é a dona única da regra** de quais janelas uma conta abre: histórico sempre,
  sessões CLI na Anthropic, sessões Codex CLI no Codex, uso e presença do time na conta Anthropic
  marcada. Morava inline na grade de cards; a HUD precisaria de uma segunda cópia das condições de
  time. `CardActionButton` é o botão de cada ação — ícone, rótulo e pisca de sessão —, e a barra do
  card passou a compô-lo: mesma ordem, mesmo desenho, `ComponentTest` sem mudança.
- No balão da conta, uma fileira embaixo com esses botões e, por último, **atualizar só esta conta**,
  com o `RefreshGlyph` do card (gira enquanto coleta, só com a política contínua). A fileira tem a
  altura reservada em toda conta: o histórico existe em todas.
- `AppShellActions` ganhou as cinco janelas por conta, extraídas dos lambdas do `DashboardScreen` em
  `main()`; o host recebe os perfis do time e os pulsos de sessão para desenhar os botões como o card.
  `HudAccount` ganhou a chave da conta do provedor (o histórico filtra por ela) e `refreshing`.
- **Clique num anel recoleta aquela conta** (`viewModel.refresh(target)`), decisão do usuário, como o
  `refreshRing` do Codenotch. O gesto do corpo agora entrega a posição do `down`, e o notch acha o anel
  pela caixa de cada conta; fora dos anéis (contagem, margem) o clique não faz nada. Continua
  **declarado** na semântica de cada anel ("Atualizar Anthropic — Padrão"), não instalado — um
  `clickable` consumiria o `down` e o arrasto pelo corpo nunca começaria.
- Coletando, o anel fica "pressionado" (escala 0,9 pela mola `SNAPPY`) e a marca gira, só com a
  política contínua.
- **O clique deixou de abrir a janela padrão.** Ela continua a um gesto: "Padrão" no balão da
  engrenagem, "Abrir" na bandeja, `Ctrl+Shift+H`; e o botão direito continua indo a "Somente cards"
  (#215). `HUD_BAR_OPEN_DESCRIPTION` virou `HUD_NOTCH_DESCRIPTION`, sem ação de clique.
