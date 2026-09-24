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
| C8 | Expandir/recolher e banners | pendente |
| C9 | Movimento da grade de cards | pendente |
| C10 | Limpeza de movimento do `ApiUsageCard` | pendente |
| C11 | Spike da janela transparente | pendente |
| C12 | Modelo puro da HUD | pendente |
| C13 | HUD em janela própria | pendente |
| C14 | Geometria de borda e migração da posição | pendente |
| C15 | O notch com anéis | pendente |
| C16 | Indicadores contínuos atrás da política | pendente |
| C17 | Verificação final | pendente |

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
