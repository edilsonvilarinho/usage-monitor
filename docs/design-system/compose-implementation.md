# Implementação em Compose — tokens, profundidade, motion, modais, tipografia e primitivas

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

**Tokens** (`presentation/ui/theme/AppTheme.kt`): quatro superfícies neutras dentro de ~14% de
luminância (`AppSurfaces`), raios 4/6/8/10 com **teto de 10** (`AppShapes`), cinco patamares de
profundidade (`AppDepth`), espaçamento 4/8/12/16/24/32 (`AppSpacing`) e motion 120/180/240/90
(`AppMotion`). Foi o gradiente **de acento** em toda superfície que fazia a tela ler como pilha de
blocos de mesmo peso; a regra que o substituiu ("card em zero, profundidade só por borda") deixou a
tela chapada, e foi revista.

**Profundidade** (`AppDepth` + `AppSurfaceLadder` + `appDepth`/`appSheen`/`appSurfaceBlock`):
`FLAT` · `CARD` · `RAISED` · `OVERLAY` · `DIALOG`, cada um com duas sombras neutras empilhadas — a
curta assenta, a larga dá distância. Painel e card em `CARD`, card com hover em `RAISED` subindo
1dp, menu em `OVERLAY`, card arrastado em `DIALOG`. Bloco **dentro** de superfície é sempre `FLAT`.
- **No escuro a sombra quase não existe, e isso foi medido**: 10dp de sombra preta escurecem
  `#131010` em 3/255. O volume ali vem da luz — linha de highlight de 1dp, brilho vertical nos
  primeiros 56dp e borda em gradiente, mais clara em cima. No claro a borda escurece embaixo e a
  sombra é o `foreground` morno, não cinza.
- **O brilho é desenhado por cima do conteúdo** (`appSheen`), porque o card pinta o cabeçalho com
  fundo próprio e por baixo dele o brilho não chegava à tela; em 4,5% de alfa ele não mexe no
  contraste do texto. A primeira passada o desenhava por baixo, e a captura saiu igual à de antes.
- **`AppSurfaceLadder` é derivado do preset, nunca retocado**: os 26 presets têm contraste medido
  contra a `surface`, e uma camada translúcida do `foreground` dá o mesmo degrau em qualquer um.
  `AppSurfaceLadderTest` afirma hover visível (≥ 1,06:1), pressão acima do hover e texto legível
  sobre a pressão nos 26; `AppDepthTest` mede a sombra e o brilho no **bitmap**, porque
  `boundsInRoot` é idêntico com e sem eles.
- **O hover do card não troca mais o fundo**: quem diz "o ponteiro está aqui" é a subida de
  patamar. O `surfaceVariant` antigo apagava o hover das linhas de cota, que usam o mesmo tom.

**Motion** (`AppMotion.Springs` + `AppMotionPolicy` + `appSpring`/`appTween`; plano
[`profundidade-movimento-hud-notch-execucao.md`](../planos/profundidade-movimento-hud-notch-execucao.md)):
tween para cor e opacidade, **mola** para posição, tamanho e escala. Três molas e só três —
`GENTLE` (dado e superfície), `SNAPPY` (seleção e pressão) e `EXPRESSIVE` (só o menu, o único
com rebote — a HUD o usava nas alças e no balão, e somado à janela crescendo o rebote lia como
tremor). **Sem overshoot em dado**: barra, anel e número passando do valor mostram, por alguns
quadros, um percentual que não é verdade.
- **`AppMotionPolicy` nasce `Static` em `AppTheme`** — transições finitas ligadas, animação contínua
  desligada. É isso que mantém `ScreenshotGenerator`, `HelpMediaGenerator`, `TourGifGenerator` e todo
  `runDesktopComposeUiTest` seguros sem cada um lembrar de desligar nada. Só o `Main` passa
  `AppMotionPolicy.forPreference(reducedMotion)`, e **a todas as janelas** — a armadilha da escala:
  janela que não recebe o valor anima e ignora a preferência, sem erro nenhum.
- **"Reduzir animações"** (Configurações → Geral → Aparência, `ReducedMotionPreferences.kt`) vira
  `snap()` em `appSpringSpec`/`appTweenSpec`: o valor chega ao alvo no mesmo quadro, e quem lê o
  estado final não precisa saber que a preferência existe. As duas funções são puras para a regra ser
  testável sem composição.
- **Modais** (`AppDialogWindow` + `AppDialog`; plano
  [`modais-abertura-execucao.md`](../planos/modais-abertura-execucao.md)): as nove janelas modais
  (Histórico, Sessões CLI e Codex, Uso e Presença do time, Chaves, Configurações, Ajuda, Novidades)
  passam por **um** host. Eram nove cópias do mesmo bloco, com divergências — duas não ativavam a
  janela.
  - **A janela nasce na primeira abertura e depois só se esconde** (`visible`), em vez de sair da
    composição. Medido no Windows 11 com a JVM aquecida: recriar custava 200–460 ms até o primeiro
    quadro, reexibir custa 30–46 ms — era essa espera o "modal lento". Os laços ao vivo continuam
    parando pelo `closeWindow()` de cada ViewModel; conteúdo com laço próprio (a demo da Ajuda) lê
    `LocalModalWindowOnScreen` para não rodar escondido. Quem guarda o assunto num anulável (a
    fonte do histórico, as notas) usa `rememberLastNonNull`, senão a janela esmaeceria vazia.
  - **A entrada espera o primeiro quadro pintado.** A escala da moldura começava ao compor, dentro de
    uma janela que o sistema mostrava de uma vez e opaca, e os quadros iniciais se perdiam no custo
    da criação. Agora a janela aparece com opacidade 0, o host espera dois quadros (com teto de
    500 ms: janela minimizada não recebe quadro e ficaria transparente para sempre) e toca o E9.
  - **E9 · filamentos de plasma** (rodada E da skill `usage-monitor-visual-options`, escolhida entre
    dez; `GargantuaModalFilaments.kt`, `filamentOpenMillis` 680 / `filamentCloseMillis` 220). A
    moldura esmaece em ~100 ms (opacidade da janela AWT) e um filamento corre sob cada linha marcada
    com `appModalRevealRow`, em ordem de leitura — topo, depois esquerda, pela caixa que a linha
    publica, nunca pela ordem de composição (a navegação lateral seria composta antes do conteúdo e
    iria inteira na frente). A linha é **recortada** atrás da cabeça: o dado está no lugar final desde
    o primeiro quadro, nada cresce nem passa do valor. Fechar recolhe da direita para a esquerda, de
    baixo para cima, e a janela só some nos últimos 15%. A escala 0,96 → 1 saiu: com o dado sendo
    revelado, o conteúdo inteiro crescendo por cima era dois gestos. As primitivas de linha
    (`AppDataRow`, `AppSectionHeader`, `AppToolbar`, `AppColumnHeaderRow`, `AppGroupBand`,
    `AppMetricBlock`, `AppStatusBar`, `AppBanner`, item do `AppSettingsNav`) já se marcam; bloco que
    não passa por elas (gráficos, a demo da Ajuda) se marca na tela. Conteúdo sem marca aparece com
    a moldura. O `AppDialog` toca o mesmo E9 no cartão, com relógio próprio.
  - **O E9 repete quando o conteúdo troca** (`AppModalRevealScope`), só nas linhas do trecho que
    trocou: seção das Configurações, tópico da Ajuda, dado que chega depois da abertura e lista ↔
    detalhe (pelo `AppStateCrossfade`, que já envolve cada estado); abas, faixa de tempo, cota e
    conta do Histórico; abas e faixa das Sessões CLI, Codex e do Uso do time; sub-aba, ordem e
    página do Resumo. O estado é uma cadeia: a linha se registra no escopo e na janela, a janela em
    movimento manda (abrir e fechar), e parada manda o escopo mais interno que toca. **A chave é do
    dado carregado, não do clique** (`rememberSettledRevealKey`): trocar a faixa relê o banco, e a
    chave do clique tocaria sobre o conteúdo antigo esmaecido e de novo na chegada. O tique do laço
    ao vivo e o filtro digitado **não** repetem — filamento a cada 5 s ou a cada tecla seria pisca.
    Repete só numa janela que abriu animada (`ModalRevealState.replayEnabled`, o mesmo critério da
    abertura).
  - **O esmaecimento é só do Windows** (`shouldAnimateModalWindow`, #340). No X11 a opacidade da
    janela é a propriedade `_NET_WM_WINDOW_OPACITY`, aplicada pelo compositor, e voltar a 1 é apagar
    a propriedade. No elementary OS o modal de Configurações ficou translúcido depois de a opacidade
    ter mudado; fora do Windows o modal abre e fecha na hora e a janela nunca sai de 1.
  - **O pedido chega por `StateFlow`, nunca por recomposição dentro da janela** (`ModalWindowHost`).
    Janela escondida não recompõe — o relógio de quadros para junto com a pintura —, e a primeira
    versão, com `LaunchedEffect(visible)` dentro da janela, abria e fechava uma vez e **nunca mais
    reabria**. Os testes de componente não têm janela e não pegariam; quem pegou foi uma sonda com o
    host real num `awaitApplication`, amostrando a opacidade da janela AWT. A corrotina que coleta
    nasce na primeira composição, que é síncrona, e o despacho continua vivo com a janela escondida.
    Pelo mesmo motivo `LocalModalWindowOnScreen` vira `false` **antes** de esconder: depois, a
    recomposição que desligaria o laço da demo não viria.
  - **Todo fechamento é a queda de `visible`**: ×, Alt+F4, Esc e os botões do conteúdo só chamam
    `onCloseRequest`. A opacidade fica onde a saída parou — restaurá-la antes de o esconder chegar à
    janela AWT pintaria um quadro cheio — e a abertura seguinte a define. Quem precisa da tela limpa
    depois de fechar (a captura do relatório de bug) espera `MODAL_CLOSE_SETTLE_MILLIS`.
  - **O nome na trilha é fixo** (`diagnosticName`): o título pode carregar o apelido do perfil, que
    costuma ser o e-mail, e a trilha vira issue pública. Cada abertura grava o tempo até o primeiro
    quadro, que é o número que prova ou desmente a lentidão.
  - **Diálogo dentro da janela é `AppDialog`, nunca o `AlertDialog` do Material**, que no desktop
    surge num quadro. Escurecimento por fade, cartão com fade e escala; saída seca, porque quem o
    tira da composição é a própria ação. O fundo ouve toque cru e não `clickable`: aquele funde a
    semântica dos descendentes, e o cartão inteiro virava um nó só — quebrou dois testes do relatório
    de bug na primeira passada.

**Tipografia**: IBM Plex Mono e Sans, carregadas do classpath por `appFontFamilies`
(`expect`/`actual`, TTFs em `desktopMain/resources/fonts/`). `label*`, `title*`, `headline*` e
`display*` são **mono** — rótulo, número, cabeçalho de coluna, onde a largura fixa do dígito alinha a
coluna; `body*` é **sans**, onde mora o texto corrido. **Não** usar `composeResources`: a carga é
assíncrona e o `ScreenshotGenerator` renderiza offscreen com relógio manual — captura com fonte de
fallback é falha silenciosa.

**Primitivas** (`presentation/ui/components/AppStructure.kt`, `AppControls.kt`, `AppStates.kt` e os
arquivos `App*.kt` vizinhos — abas, navegação lateral, profundidade, chips, menu e tooltip):
todas stateless. Corpo de janela com barra de estado, barra de controles, superfície de dados,
cabeçalho de seção com marcador de 2dp, linha de dados com divisória própria, bloco de métrica,
faixa de legendas de coluna com valor de célula, abas sublinhadas, controle segmentado, chip de
alternância, botão, botão de ícone, campo, interruptor, tooltip, aviso, vazio, carregando, erro,
indicador de estado e barra de progresso.
Antes de desenhar um retângulo novo, procure aqui.

- **Primitiva construída e não adotada não conserta nada.** A refatoração de agosto fechou com
  `AppWindowScaffold` e `AppToolbar` em **zero** telas e `AppStatusIndicator` em uma — a fundação
  existia e cada tela continuava montando o próprio retângulo. A passada de conformidade de
  2026-08-23 fez a adoção; ao criar primitiva nova, o commit que a cria e o que a consome andam
  juntos.
- **Bloco de métrica** (`AppMetricBlock`): rótulo em cima, valor embaixo, borda em volta, largura
  fixa e igual entre os blocos da mesma fileira. A ordem não é estética — numa fileira de quatro, o
  olho varre os rótulos para achar o que procura, não os números. Qualificação longa fica **fora**
  do bloco: dentro dele, um rodapé de quatro medidas mede três vezes a largura do bloco vizinho e a
  fileira perde o alinhamento que a grade existe para dar.
- **Número é `label*`, não `body*`.** A divisão entre as duas famílias é por papel: `body*` é sans e
  existe para texto corrido, e número em fonte proporcional não alinha coluna — que é a razão de a
  mono estar na escala. Vale para célula de tabela, valor de métrica e rótulo de controle.
- **Controle deslizante** usa os slots `track`/`thumb` do `Slider` do Material com o desenho do
  sistema (trilha de 4dp, polegar de 12dp). Não é um controle próprio: a semântica de progresso, que
  é o que `SetProgress` dos testes exercita, tem de continuar vindo do `Slider`.
- **`AppSwitch` ligado é verde**, não azul: ligado é um estado, o mesmo "ok" do indicador e da barra
  saudável. O azul deste sistema é informação — linha de gráfico e realce de seleção —, e com ele
  ali um interruptor ligado lia como item selecionado.

- **Aba × segmentado × chip**: aba troca **o que** a tela mostra, segmentado troca **como** (janela,
  ordem, tamanho de página), chip de alternância liga ou desliga **uma** restrição. Desenhá-los igual
  foi o que fez o app usar a mesma pílula para as três coisas.
- **Cor nunca informa sozinha**: todo estado carrega ponto e palavra (`AppStatusIndicator`), e o tom
  sai de `AppTone`, que lê de `AppAccents` e do `ColorScheme` — nunca de um literal novo. Foi assim
  que o âmbar do semáforo de risco passou anos abaixo de 3:1 contra a superfície clara.
- **Acento é identidade de fonte, não de valor**: ele vive no marcador de 2dp e na linha do gráfico.
  Custo em azul e tempo em verde na mesma tabela sugerem categorias que não existem.
- **Marca do fornecedor** (`AppProviderMark`): a única exceção a "sem biblioteca de ícones". O
  asterisco do Claude, o nó da OpenAI, o cubo do Cursor e os demais são caminhos SVG monocromáticos
  (Simple Icons CC0 e lobe-icons MIT, o mesmo conjunto do ai-usagebar), tingidos pelo acento no
  cabeçalho do card e pela cor do texto no anel da HUD. Identificação não é glifo de controle, que
  continua Unicode. Decorativa para a semântica: o nome está sempre escrito ao lado. `when`
  exaustivo sobre `ApiSource`: fonte nova sem marca não compila, e `AppProviderMarkTest` pega o SVG
  que perdeu um caractere na cópia (o parser devolveria caminho vazio, sem erro).
- **Cor por conta** (`AccountAccent` + `accountAccentColor` + `AppSwatchChip`; issue #275): várias
  contas Claude no mesmo PC vestiam o mesmo azul, e só o título as separava. Cada perfil pode
  escolher uma de oito cores em Configurações → Contas. **Paleta fixa, não seletor livre**: cada cor
  tem variante clara e escura, e `AppAccentsContrastTest` mede as dezesseis pela régua dos acentos
  de fonte. A escolha mora no nó do perfil (`color` em `AnthropicProfileRegistry`), como **nome** do
  enum. Nome desconhecido vira "Padrão"; renomear um valor apaga a escolha de quem o tinha.
  **Enum novo**, e não valor em `AppAccents`, que é a identidade do fornecedor.
  - **A cor substitui o acento só onde ele já aparece**: marcador de 2dp e marca do card, cabeçalho
    do balão da HUD e marcador da linha do perfil. Nunca pinta superfície. `accountAccentColor` é a
    dona única, e sem escolha devolve o acento da fonte.
  - **No miolo do anel da HUD a marca só ganha cor com escolha.** Sem escolha continua na cor do
    texto, pela razão de sempre (o acento competiria com a cor de risco dos arcos). Com duas contas
    Claude o miolo é o único ponto do notch recolhido que diz qual é qual, e ali a escolha é do
    usuário. Um marcador à parte mudaria `hudNotchSizes` e dividiria espaço com a órbita de sessão
    ativa. A cor chega à HUD dentro do próprio `HudAccount` (`accountAccent`), e não como parâmetro
    a mais na cadeia do notch.
- **Emoji por conta** (`AccountEmoji` + `AccountEmojiGlyph` + `AppGlyphChip`; issue #287): a cor
  só separa duas contas Claude para quem lembra qual tom é de qual conta. Cada perfil pode ter um de
  dezesseis emojis em Configurações → Contas, gravado como a cor (`emoji` no nó do perfil, **nome**
  do enum, desconhecido vira "Nenhum"). Aparece como **selo no canto de cima à direita do anel** da
  HUD, ao lado da marca no cabeçalho do balão e do card, e antes do apelido na linha do perfil.
  - **É conteúdo do usuário, não cromo.** A regra "sem emoji" do design system continua valendo para
    a interface; este glifo é da natureza do apelido e o nome está sempre escrito ao lado, por isso
    ele é decorativo na semântica.
  - **Conjunto fixo, não campo livre**, pelo motivo da paleta: cada glifo é um code point só, com
    apresentação de emoji por padrão, e foi visto renderizado em cor pelo Compose no Windows — na fonte
    mono e **offscreen**, então os geradores de captura também o desenham. Campo livre traria
    sequências ZWJ, tons de pele e bandeiras de largura imprevisível, e quadrado vazio onde falta a
    fonte. **Linux e macOS não foram vistos**: dependem da fonte de emoji do sistema.
  - **O selo não entra em `hudNotchSizes`.** Ele passa `HUD_EMOJI_BADGE_OVERSHOOT` (4dp) para fora do
    anel, dentro do respiro que o notch já tem — `HudNotchGeometryTest` afirma o limite e a geometria
    igual com e sem emoji. `AccountEmojiGlyph` mede o glifo em **dp**: em sp ele cresceria com a
    escala de fonte do sistema e sairia da caixa que a geometria conta.
  - Mapa paralelo ao das cores (`accountEmojis`), e não um objeto de identidade que juntasse os dois:
    a cor já atravessava cinco assinaturas, e trocá-las todas por causa do emoji mexeria em código
    que a issue não pede.

### Armadilhas de teste de tela

Movidas do `CLAUDE.md` em 2026-09-28 pela skill `usage-monitor-token-cleanup`; o `CLAUDE.md` guarda
o ponteiro. **Cada uma custou uma suíte vermelha**:

1. `weight` dentro de `FlowRow` não tem referência de largura: o Compose deixa o filho **sem
   posicionar** e o sintoma é `assertIsDisplayed` falhando com `boundsInRoot` válido.
2. Ação que virou ícone precisa de `contentDescription` na **semântica**, não só de `onClickLabel` —
   é `onNodeWithContentDescription` que as suítes usam. `AppIconButton` já traz os dois.
3. `BasicTextField` mescla descendentes: o placeholder precisa de `clearAndSetSemantics`, ou o campo
   vazio passa a "conter" o texto de exemplo e duplica nós para o `onNodeWithText`.
4. Tela que ficou mais alta obriga a subir a altura da **cena** do teste de componente (1024 × 768
   por padrão), nunca a do `Box` interno — o `Box` não é o que limita o `LazyColumn`.
5. O `modifier` de um campo composto desce até o `BasicTextField`, não fica na coluna: ele carrega a
   `testTag`, e `performTextInput` exige o `RequestFocus` que só o campo tem.
6. Borda que precisa ocupar layout é **fundo mais padding**, nunca `Modifier.border`: ele arredonda o
   traço para cima e pinta sobre o conteúdo, e só bitmap (`captureToImage`) pega o defeito — seção
   abaixo.

### Armadilha: `Modifier.border` numa caixa fina (issue #83)

`Modifier.border` arredonda o traço **para cima** (`ceil(width.toPx())`, `Border.kt`) e o pinta
**depois** do conteúdo. Numa caixa de 4dp o anel de 1dp vira 2px a partir de densidade 1,05 e come
a caixa inteira: a barra de cota ficava cinza com a cota em 37% nas escalas de 105% e 110%
(issue #83). Borda que precisa ocupar layout é **fundo mais padding** — o `roundToPx` do padding
acompanha a altura, e é o `box-sizing: border-box` que o protótipo já especificava. O defeito é de
**pintura**: `boundsInRoot` devolvia a altura cheia nas duas escalas, então só bitmap
(`captureToImage`) o pega.

`ShimmerBox` foi apagado — não tinha chamador.
