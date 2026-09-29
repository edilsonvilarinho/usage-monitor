# Barra HUD — notch: decisões e histórico

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

**Barra HUD — notch** (`HudWindow.kt` + `HudNotch.kt` + `HudBalloon.kt` + `HudHandles.kt` +
`HudNotchGeometry.kt` + `HudModel.kt` + `AppShellActions.kt` + `CardActions.kt` + `AppUsageRing` +
`HudWindowPreferences.kt` + `AppWindowAnchor.kt`; issue #164, redesenhada no plano
[`profundidade-movimento-hud-notch-execucao.md`](planos/profundidade-movimento-hud-notch-execucao.md)):
**o único modo de visualização do app** desde setembro de 2026 (plano
[`hud-modo-unico-execucao.md`](planos/hud-modo-unico-execucao.md)): a janela principal com o dashboard,
o modo somente cards e o seletor de modos saíram. O que existe é um **notch colado numa borda da
tela** numa janela própria, transparente, sem decoração e sempre no topo (`HudWindowHost`), sempre
composta. Ela é a âncora do app (`anchorAppWindow`): pai do diálogo de arquivo, alvo da captura do
relatório de bug, janela ativada pela bandeja e pela segunda instância, e o ponto em que o ACK de
atualização sai. O desenho vem do Codenotch; a regra de conteúdo vem das seis versões da barra de
linhas que ele substituiu. `HudEdge` é enum novo.
- **Um anel por conta, um arco por cota** (`AppUsageRing`, até três concêntricos). **A janela mais
  longa fica por fora** (`HudAccount.rings`, issue #278): mensal, semanal, a janela curta, e saldo e
  créditos (`REPORTED`) por dentro. Na ordem da API a 5h ficava por fora da semanal, o contrário de
  como se lê um alvo. A seleção continua sendo as três primeiras cotas; só a ordem muda, e é estável.
  O pulso de atenção segue o anel da cota em foco (`attentionRingIndex`), não o de fora fixo. No
  balão cada cota leva um glifo dos anéis com o dela aceso, e a descrição do anel diz a posição em
  palavra ("anel externo 7d 9% · anel interno 5h 28%"). O Codenotch faz um anel por fornecedor com a pior janela, e um percentual só
  esconde a 7d estourada atrás de uma 5h em 12%. Ao lado, **uma linha por anel com a janela**
  (`HudAccount.stripLines`: "7d 72%" sobre "5h 45%", na ordem dos anéis) e a **palavra do estado**:
  cor nunca informa sozinha. Cota sem projeção tem a trilha **tracejada**.
  - **Era um número só, o da cota em foco, sem dizer a janela** (issue #286). O foco é o pior risco, e
    ele troca de janela sozinho: o mesmo lugar dizia 45% numa coleta e 72% na seguinte sem nada ter
    mudado no consumo. As linhas não mudam de lugar. A janela vai em `onSurfaceVariant` e o número em
    `onSurface`; a cor de risco fica no arco e na palavra, senão ela informaria o estado sozinha.
    **Exceção desde a #322** (`HudAccount.emphasizedStripLineIndex`): em `Atenção`/`Crítico` o
    **número** da pior janela vai no tom — a pílula ao lado escreve o estado, e o tom só aponta qual
    janela o causou. Em dia nenhum número ganha cor: pintar de verde todo percentual somaria cor sem
    informar nada. O rótulo da janela continua neutro. Conta
    de cota única continua com o número em `labelMedium`, sem rótulo. O preço é a espessura: cada
    janela é uma linha `labelSmall` de 14dp (`HUD_STRIP_LINE`), e o notch de cima fica nos 44dp do anel
    com uma, 46dp com duas e 60dp com três (a palavra em pílula, #322). **O foco continua** (`HudAccount.focusIndex`/`focusLine`) no pulso de
    atenção, na célula compacta e na bandeja, onde não cabe uma linha por anel. A palavra continua
    sendo a do **pior** risco da conta: com as janelas à vista ela resume a conta, não um número.
  - **A palavra é uma pílula tonal** (`AppStatusPill`, issue #322): ponto, palavra no tom e fundo do
    tom a 8% (`STATUS_PILL_TINT_ALPHA`). Solta, tinha o peso dos percentuais e o notch lia "flat". Os
    8% são medidos: com 14% o verde do tema claro caía para 4,17:1, e `AppStatusPillContrastTest`
    guarda os três tons nos dois temas. A geometria lê as constantes da primitiva
    (`statusPillWidth`/`statusPillHeight`) e soma 1dp de arredondamento — sem ele,
    `HudNotchTextFitTest` media até 0,6dp a mais entre 110% e 144%. Com duas janelas o notch de cima
    vai a 46dp (28 das linhas + 18 da pílula). O estado sem contas continua `AppStatusIndicator`.
  - **Com contas demais para a borda a faixa fica compacta** (`HudNotchSizes.compact`, E9): se a faixa
    completa passa de `HUD_MAX_ALONG_FRACTION` (45%) do comprimento da borda, cada conta vira a célula do
    Codenotch — anel e a cota em foco com a janela embaixo (`focusLine`, "7d 72%"), sem a palavra. Com sete APIs numa tela de notebook a faixa
    completa atravessava a borda de cima; compacta ela cai para menos da metade. A palavra não some da
    HUD: fica no cabeçalho do balão e na descrição do anel. Com poucas contas nada muda.
- **O notch não cresce; o detalhe é um balão de uma conta só** (`HudBalloon`), como o card do
  Codenotch: o ponteiro sobre um anel abre, ao lado do notch e do lado de dentro da tela, o balão
  **daquela** conta — o painel com todas as contas empilhadas saiu (rodada 3). Cabeçalho com marca,
  título e estado; por cota o título do card ("Sessão 5h") e "Reinicia 22h59" (#189), barra e
  **"68% usado · 32% restante"** (`hudUsedLeftText`: usado truncado como o anel, restante derivado
  do usado exibido, "<1%" nas duas pontas, nada para saldo e atividade observada); cotas do mesmo grupo
  (Antigravity, Cursor) numa caixa sob o nome dele; o rodapé **"Plus · via Codex"** — plano e origem da
  leitura, `hudSourceOrigin` com `when` exaustivo sobre `ApiSource`; e os **botões do card**. A cauda
  (a cunha do `TooltipTail` do Codenotch) aponta para o anel, e trocar de anel repete a abertura pelo
  jato a partir do anel novo (ver as alças, abaixo).
- **Instalação nova** (issue #277, revista com a HUD como único modo): sem API habilitada o notch diz
  "Nenhuma API" em vez de "Carregando" (`hudFallbackLabel`), e as Configurações abrem sozinhas uma vez
  por arranque (`OpenSettingsWithoutApis` em `Main.kt`) — fechá-las não as reabre, e a engrenagem
  continua levando até lá. A troca automática da janela padrão para a HUD na primeira coleta
  (`hudDefaultPending`, notificação com os caminhos de volta) saiu junto com a janela padrão.
- **Sinais de sessão CLI no balão** (`HudSessionSignal` + `hudSessionSignals`; issue #265): a seção
  "Sessões CLI", entre as cotas e o rodapé, só quando há o que dizer. Uma linha por sinal: contexto
  saturado, contexto crescendo (as duas contagens saem do mesmo `SessionPulse` que faz o botão de
  sessões piscar) e sem resposta (`stalledSessions`, que antes só ia para a bandeja). O texto usa as
  palavras do dado — "Contexto saturado · 1 sessão", "Sem resposta há 2h10" — e **nunca** "Atenção",
  que é a palavra do risco de cota, nem "aguardando você": a sessão sem resposta é o pedido do
  usuário esperando o modelo, o contrário disso, e há teste afirmando as duas proibições. Sessão sem
  resposta **com perfil nulo não acende conta nenhuma**, pelo mesmo motivo de sempre: conta nula não
  é "todas as contas". O notch em repouso não muda — o pulso âmbar continua sendo só do risco de cota.
- **Os botões do card têm dona única** (`cardActionsFor`): histórico sempre, sessões CLI na Anthropic,
  sessões Codex CLI no Codex, uso e presença do time na conta marcada. A barra do card e o balão compõem
  o mesmo `CardActionButton`; o balão acrescenta "atualizar só esta conta". As ações moram em
  `AppShellActions`, montadas **uma vez** por `buildShellActions` (`AppShellActionsFactory.kt`) e consumidas pelo `DashboardScreen` e pelo host.
- **Alças nas pontas** (`HudHandles.kt`), o `MoveHandle` e o `SettingsOrb` do Codenotch: com o notch
  aberto, a **mão** (ponta de perto) move — **só ela**: arrastando pelo corpo o notch saía do lugar
  quando a intenção era clicar num anel — e a **engrenagem** (ponta
  de longe) abre o balão com **tudo o que o rodapé oferece**: contagem (o único lugar dela na HUD), os três modos de janela em
  linhas (o menu do rodapé é `Popup` e seria recortado pela janela) e o próprio `FooterActionGroup`.
  Paradas, as alças são um arco de um quarto na margem de sombra que a janela já tem — nenhuma área
  nova engolindo clique. Carregando, a mão **fica na composição**: tirá-la cancelaria o gesto.
  - **A engrenagem abre no hover, como o anel** (#317). O clique também abre e **nunca fecha**: com o
    hover abrindo, alternar fecharia o balão que o próprio ponteiro acabou de abrir. Fechar é sair do
    notch ou passar num anel. O reinício do app continua sendo o clique no botão do balão — abrir no
    hover não torna o reinício um gesto de rotina.
  As alças entram **deslizando de dentro do notch**, com fade e escala pela mola `GENTLE`. O balão
  (de conta e da engrenagem, o mesmo) abre pelo **jato relativístico (B3)**: um feixe fino sai do
  centro do anel pela cauda e atravessa o balão, que se desdobra ao longo da borda a partir da linha
  do feixe (`jetOpenMillis` 520ms); fechar dobra de volta e recolhe o feixe para dentro do anel
  (`jetCloseMillis` 240ms). Quadro puro em `GargantuaBalloonJet.kt`, movido pela transição do
  `AnimatedVisibility` (a saída espera o quadro terminar); recorte e feixe desenhados pelo `HudBalloon`
  sem mudar a caixa, e o feixe atravessa o notch por fora dela. O recorte da janela volta
  `jetCloseMillis` + 60ms depois de recolher, senão cortaria o fim do fechamento. Escolhido entre 5
  protótipos HTML (onda de choque, lente gravitacional, jato, luz de acreção no contorno, ondas
  gravitacionais). **Trocar de anel (ou ir para a engrenagem) com o balão aberto repete o jato a partir
  do anel novo**: o balão salta para lá (`HudBalloonPlacement.index`), sem deslizar nem crossfade — o
  usuário pediu a animação também na troca, e desdobrar enquanto desliza lia como tremor.
- **Identificação, como no Codenotch e no ai-usagebar**: a **marca do fornecedor** (`AppProviderMark`)
  no miolo de cada anel, na cor do texto — em volta dela os arcos já carregam a cor de risco —, e no
  cabeçalho do balão no acento da fonte. O rótulo da conta é o **título do card**
  (`ApiUsageStats.displayTitle`, dono único: "Anthropic — Padrão"); a HUD mostrava só "Padrão" e
  escondia de quem era a conta. O **plano** ("Max 20x", "ChatGPT Plus") vem no rodapé do balão e na
  descrição do anel. O anel passou de 28 para 36dp para a marca caber no miolo, e de 36 para 44dp (issue #322) porque
  a marca ainda ficava pequena: 14dp com duas janelas e 8,4dp com três. A fórmula de `hudRingMarkSize`
  não mudou; com o anel maior ela dá 25,2 / 19,6 / 14dp.
- **Resumo na bandeja** (`hudTraySummary`): o tooltip do ícone lista cada conta com o percentual em
  foco **e a janela dele** — "Usage Monitor — Anthropic — Padrão 7d 87% · Codex 0%" (#286) —, cortado com reticências nos 127
  caracteres do `szTip` do Windows.
- **A ordem é a dos cards** (`orderedByCardOrder`, em `buildHudAccounts`), nunca a do risco: com o
  risco mandando, a primeira conta trocava sozinha. `buildHudAccounts` é função pura de `commonMain`
  com teste próprio — a regra morava inline no antigo `main()`, sem teste.
- **Forma** (`HudNotchShape`): reta e rente na borda, cantos de 14dp do lado de dentro e **ombros
  côncavos** de 8dp ligando os dois. Isenta do teto de raio de 10dp: é silhueta, não painel. Desenhada
  para o topo e levada às outras bordas refletindo/girando os pontos, de controle inclusive.
  Profundidade `DIALOG`, brilho de topo, borda com luz.
- **Clique em pixel transparente é engolido** — medido no Windows 11, com os renderizadores padrão,
  `SOFTWARE` e `OPENGL` (C11 do plano). Por isso a janela parada só aceita clique no notch: ela tem
  sempre o tamanho da aberta, e a área de clique é recortada por `Window.shape` (`hudRestHitRegion`:
  notch recolhido mais a margem de sombra de 16dp nas bordas de dentro). Quando o ponteiro entra o
  recorte sai **antes** de o balão abrir; ao sair, o balão some (150ms de espera contra o `Exit` de um
  quadro na divisa) e o recorte volta **depois** (200ms). **Nenhum redimensionamento AWT por quadro** —
  era o tranco da barra anterior, que interpolava a janela. O hover é a **união** de corpo, balão e
  alças: o caminho do anel ao balão passa pela cauda, que é opaca e do balão.
  - **A janela não muda de origem nem de tamanho ao abrir** (`hudDockedWindowBounds`, issue #294).
    Janela transparente que muda de origem mostra um ou dois quadros do conteúdo antigo no lugar novo
    — medido no Windows 11 com captura de tela, com o redimensionamento do Compose (tamanho e posição
    em duas chamadas AWT), com `setBounds` numa chamada só e com ele aplicado antes do estado. A E11
    fixou a origem só ao longo da borda; na direita e embaixo a espessura ainda andava, e o notch da
    direita aparecia 274px para dentro da tela a cada abrir e sumia a cada fechar (spike da #294:
    39–49 de ~668 quadros fora do lugar). Com a janela fixa e o recorte alternando: **0 de 670**, e o
    clique fora do recorte chega à janela de baixo (sem recorte, engolido).
  - **O recorte também corta a pintura**, e por isso a sombra e os arcos de dica das alças cabem na
    margem de 16dp. Sem suporte a `PERPIXEL_TRANSPARENT` o `setShape` lança e a HUD segue sem
    recorte: a área do balão volta a engolir clique, mas o notch não pisca. `HudHitRegionApplier` guarda o último retângulo porque `Window.getShape()` devolve cópia
    em `Path2D`, que nunca é igual ao pedido.
  - **O recorte é só do Windows** (`hudUsesHitRegion`, issue #340). Relato de usuário no elementary OS 6.1
    (Ubuntu 20.04, X11, Gala), notch na borda direita: com o ponteiro em cima, o balão aparecia só
    como a tira de 16dp da margem, com a cauda, e as alças saíam como círculos cortados. O
    `shape = null` da abertura não tirava o recorte ali. **Regressão da #294** (`d3ff05b`, v41.0.0):
    o mesmo usuário, na mesma máquina, abria o balão inteiro até 25/09, quando a janela ainda
    redimensionava ao abrir e não havia recorte. O Windows é a única plataforma medida, e só
    nele o host aplica `Window.shape`; no Linux e no macOS a janela fica sem recorte — o balão abre
    inteiro, e o preço aceito é a área vazia dele poder engolir o clique da janela de baixo com o
    notch parado. Esse custo no Linux ainda não foi medido.
- **O tamanho é da geometria, não da composição** (`hudNotchSizes`): a janela é dimensionada antes de
  existir composição, e medir para devolver fecharia o laço `redimensionar → recompor → medir`. A
  estimativa usa o avanço da Plex Mono — a escala `label*` é mono, e é isso que torna o número
  calculável. O notch e o balão usam **os mesmos números** (`requiredSize`; o balão por
  `hudBalloonHeight`/`hudAppBalloonHeight`, linhas de altura fixa), e `HudNotchTest` afirma nas quatro
  bordas que o notch tem o tamanho recolhido parado e aberto, que o balão de cada conta cabe inteiro na
  janela aberta e que a coluna dele mede o que a geometria soma: é a costura que a barra antiga quebrou
  quando o padding que a geometria não contava cortou o texto ao meio. A área aberta reserva o balão
  **mais alto** — trocar de anel não redimensiona a janela. A largura parada é o maior entre
  percentual e palavra, e uma coleta que troca `9%` por `88%` não mexe na janela.
  - **Cada texto leva 1dp de folga** (`charWidth`): o Skia arredonda a linha para cima em pixel
    inteiro, e em densidade fracionária (115% sobre 125% do Windows) as diferenças somavam e a
    contagem, último item da faixa, quebrava em "04:5". Em densidade 1 — a dos testes de componente —
    as contas batem, e por isso só `HudNotchTextFitTest`, que varre 100%–200%, pega.
  - **O centro do notch é preso reservando as alças** (`reserveAlong`, `hudDockedWindowBounds`): o
    notch não anda na tela ao abrir perto de um canto, e as alças nunca ficam fora da tela. Durante o arrasto a janela
    é `withHandles`, simétrica, e o centro dela continua sendo o do notch.
  - **O arrasto parte de `hudDragWindowBounds` e mede pela janela de arrasto, nunca por `windowSize`**
    (issue #288). Só dá para pegar a mão com o notch aberto, e o gesto guarda os lambdas da composição
    em que começou: com `windowSize` ali, o primeiro passo prendia à tela uma janela da **largura do
    balão** e a empurrava 274dp para dentro — só embaixo e à direita, onde a janela aberta é recuada —,
    e como o passo é incremental o vão seguia o arrasto inteiro; o encaixe lia o centro dessa mesma
    largura errada e soltava o notch na borda errada. Medido com o app real e ponteiro sintético: antes
    a mão ficava ~270px ao lado do ponteiro, depois fica sob ele. `HudNotchGeometryTest` afirma o notch
    no mesmo ponto parado, aberto e no começo do arrasto, nas quatro bordas.
- **Posição é borda + fração** (`HudPlacement`, chaves `hudEdge`/`hudEdgeOffset`): sobrevive a troca
  de resolução e de monitor. Arrastar solta o notch da borda; ao soltar, `nearestHudPlacement` o gruda
  na borda mais próxima do **centro** dele. A
  posição da pílula antiga (`hudWindowX/Y`) migra uma vez e as chaves velhas são apagadas. Estreia no
  topo em 82%, onde a pílula nascia, e não no centro, onde fica o título de janela maximizada.
  - **Parado, aberto e encaixado o notch mora na área útil, fora da barra de tarefas** (issue #288).
    A #256 o deixava ocupar a faixa da barra, mas no Windows ela também é *topmost* e volta para cima
    de toda janela *topmost* a cada clique, hover ou notificação: o `alwaysOnTop` perde essa disputa,
    e o notch de baixo ficava meio coberto. Só o **arrasto** continua livre sobre a tela inteira. O
    monitor segue identificado pelos limites **inteiros**, que não mudam quando a barra é movida.
    Barra com ocultação automática não reserva área útil e continua podendo cobrir o notch quando
    sobe — escolha de quem a oculta.
  - **E o monitor** (`hudScreenId`/`hudScreenBounds`, issue #273). Borda e fração eram resolvidas
    sempre contra o monitor padrão, então o arrasto era preso a ele e o notch nunca saía do primário.
    Agora o arrasto e o encaixe usam o monitor **sob o ponteiro** (`MouseInfo.getPointerInfo().device`),
    e o monitor é gravado com id **e** limites, porque o Windows renumera `\\.\DISPLAYn` ao
    reconectar. Ele é resolvido de novo a cada abertura por hover. Monitor desligado cai no padrão
    **sem apagar** a gravação: quando ele volta, o notch volta junto.
- **Um gesto só** (`hudPressGesture`): **clique num anel recoleta aquela conta** (decisão da rodada 3,
  como o `refreshRing` do Codenotch — o gesto entrega a posição do `down` e o notch acha o anel pela
  caixa de cada conta; fora dos anéis nada acontece), com o anel "pressionado" enquanto coleta; o botão
  direito é engolido sem ação (levava ao modo somente cards, removido). No corpo o
  gesto é `draggable = false`: passar do limiar só desiste do clique, e **mover é só pela mão**, que usa o
  mesmo gesto com arrasto. A ação de cada anel é **declarada** na semântica, não instalada por `clickable`, que
  consumiria o `down`. Nenhuma coordenada sai do composable: o host lê o ponteiro na tela por
  `MouseInfo`, incremental. Não há saída para outra janela: "Abrir" da bandeja e a segunda instância
  trazem a janela da HUD para a frente (`focusHud` → `activateWindow`), e `F1` é o único atalho.
- **Contagem até a próxima coleta só no balão da engrenagem** (#185, #269): ela ficava no fim da
  faixa, e com a cadência adaptativa (60 s com sessão CLI ativa) virou um número que reiniciava a cada
  minuto na borda da tela. A faixa passou a ser só das contas — `hudNotchSizes` não tem mais
  `showsCountdown`, e o notch parado encolheu uma linha na lateral e a largura da contagem no topo. O
  rodapé do modo padrão continua com ela. O tique mora no `HudCountdown` e tem o interruptor
  `updatesEnabled`, porque sob o relógio dos testes o laço giraria para sempre.
  - **O ícone é um relógio que esvazia, e fica numa linha só com o tempo** (`HudCountdownClock` +
    `hudRefreshFraction`; #293). Na lateral, ícone e `05:42` ocupavam duas linhas. O relógio é um
    setor de 12dp: começa cheio logo depois da coleta, esvazia no sentido horário a partir das 12h
    e volta cheio na coleta. A volta inteira é `DashboardViewModel.currentPollInterval` — 60 s ou 5 min, a cadência em vigor (#269). Sem o intervalo,
    o ícone volta a ser o ↻.
  - **Um filete na borda interna foi tentado e recusado**: sem número ao lado, ninguém entendia o
    que ele media, e rente à borda ele se lia como o próprio contorno do notch. O que deu sentido
    foi o relógio **ao lado do número**: o número diz quanto falta, e o relógio diz que é contagem.
  - O passo de cada segundo desliza em 900 ms: são transições finitas, uma por tique, e não
    animação infinita, e por isso não travam o `waitForIdle`. Com "Reduzir animações", o passo vira
    salto.
- **Atualização pendente é o ponto da engrenagem, sem clique no notch** (#225, #291). Ela não ocupa
  a faixa de anéis: o ícone `SystemUpdate` que ficava ali era um celular com seta, e em 12dp ninguém
  o lia como "versão nova". Parado, o arco de dica da engrenagem toma o tom do estado e ganha um
  ponto; aberto, a engrenagem leva um ponto no canto, e a frase inteira vai na descrição dela — cor
  nunca informa sozinha. O notch recolhido tem o mesmo tamanho com e sem atualização, e há teste
  afirmando. Clique nenhum no notch reinicia o app: seria clique de rotina.
  - **O balão da engrenagem mostra um `AppBanner` e um `AppButton`**, não frase colorida e rótulo
    com seta — aquele só parecia clicável no hover. O texto vem partido de `updateBannerContent`
    (`headline` numa linha, `detail` em até duas), porque o título de uma linha da faixa não cabe
    nos ~202dp de texto do banner. A cor fica só na barra de 2dp. A ação é a **mesma da faixa** do
    modo padrão, despachada por `updateBannerAction`, dona única do `when` por estado. Baixando não
    tem botão, como na faixa.
  - **A faixa do modo padrão também ganhou botão**, e deixou de ser clicável inteira: com o botão
    dentro dela, clicar fora dele faria a mesma ação sem nada indicar. O preço é a faixa passar de
    ~34dp para ~46dp de altura, pela altura de controle do botão.
- **Coleta: ondas gravitacionais** (R1, `drawGargantuaRefreshLight`, `shouldPlayRefreshWave`;
  2026-09-28). Substituiu o anel "pressionado" (escala 0,9) e o pulso da marca (issue #322), que o
  usuário achou ruins; escolhida entre quatro protótipos HTML (ondas, varredura de sonda, recarga de
  plasma, tique-taque). Coletando: três ondas finas defasadas saem do anel a cada `rippleMillis`
  (1,3s) e o disco acelera para `refreshMillis` — contínuo, só com `continuous && !reduced`.
  Concluído: o plasma desliza do valor antigo ao novo (a mola de sempre) e uma onda final mais forte
  toca uma vez (`refreshWaveMillis`, 800ms) quando `refreshing` cai de verdadeiro para falso — também
  em coleta que falhou: a onda diz "o app olhou agora", não "o número mudou". É finita, então só
  "Reduzir animações" a desliga. A marca não gira nem pulsa mais.
- **Dado novo: horizonte de eventos** (D5, `GargantuaRoll.kt`; 2026-09-29). Quando o texto de uma
  linha ("7d 56%" → "7d 61%") ou a palavra da pílula muda, só os caracteres diferentes rolam como
  odômetro: o antigo sobe e some, o novo nasce de baixo, e a base de cada um acende uma borda fina de
  luz quente (`rollMillis` 480ms, cascata `rollStaggerMillis` 50ms, curva padrão sem rebote). O
  começo comum não rola (o rótulo "7d " fica parado) e o resto compara pela direita
  (`gargantuaRollGlyphs`: "9%" → "12%" rola o 9 e faz nascer o 1). A pílula rola a palavra inteira
  (`AppStatusPill(rollLabelChanges = true)`, só na HUD). O efeito é **desenho** sobre o `Text` do
  valor novo — recorte dos que mudam e o antigo medido por `TextMeasurer` —, então a geometria, que
  mede os textos antes da composição, não muda. Não rola na primeira composição nem com "Reduzir
  animações". Escolhido entre 5 protótipos HTML (desvio para o vermelho, lente, plasma que conta,
  onda gravitacional, horizonte de eventos).
- **Sessão ativa e atenção são movimento contínuo, atrás da política**: o arco fino que gira **em
  órbita por fora** do anel (turno CLI nos últimos 5 min, `SessionPulseViewModel.activeTargets`) e o pulso do
  anel de fora em `Atenção`/`Crítico` só existem com `AppMotionPolicy.continuous`. Sem ela o arco
  fica parado e o pulso some; a palavra continua dizendo o estado.
  - **Mais suave desde a #322.** A órbita virou **cometa**: 130° com a cauda num gradiente que se
    dissolve até sumir e um ponto na cabeça, uma volta em 2,4s (era um segmento chapado de 90° em
    1,4s, que lia como indicador de carregamento). A atenção **respira** em vez de piscar: o arco fica
    entre 0,8 e 1 de opacidade e um halo com o dobro do traço, até 28% de opacidade, cresce e some em
    1,6s com aceleração suave nas pontas (era o arco inteiro oscilando 0,35↔1 em 0,9s). E na primeira
    composição os arcos **se desenham** a partir de zero pela mola `GENTLE`, juntos — antes surgiam
    cheios. Com "Reduzir animações" nascem no valor. Houve escalonamento de fora para dentro por espera
    em quadros; no relógio manual dos testes o arco de dentro não assentava (medido: 136 pixels mudando
    entre 6,0s e 6,5s), e ele foi retirado.
  - **Reflexo em repouso** (issue #322, pedido depois: "os círculos estão muito estáticos mesmo sem
    atualização"). Com a política contínua, um reflexo branco de 48° com cauda que some corre dentro de
    cada arco, do início até a ponta, com opacidade subindo e descendo por um seno (pico 42%). Uma
    volta a cada 4,2s, dos quais pouco mais da metade é pausa; cada arco de dentro sai 22% da volta
    atrasado, então o anel nunca acende inteiro. O reflexo nunca passa da ponta do arco — ali ele
    mentiria um percentual maior.
  - **Brilho da trilha e reflexo mais forte** (issue #322, depois de olhar no app: "os círculos de 5h
    e 7d estão muito estáticos"). A primeira versão do reflexo não se via: 48°, pico de 42% num traço
    de 2,5dp, mais de metade do ciclo em pausa — e o anel do Codex em 3%/0% ficava **inteiramente**
    parado, porque arco abaixo de 12° não recebia reflexo. Agora: o reflexo tem 64°, pico de 65%, uma
    passagem a cada 2,8s com pausa curta e mínimo de 6°; e uma faixa de luz de 80° a 22% gira pela
    **trilha** de cada arco a cada 3,6s, defasada 120° entre arcos, com ou sem consumo. A trilha é "o
    que falta", não o dado — iluminá-la não sugere percentual. `HudNotchTest` afirma que um anel em
    1%/0% se mexe com a política contínua (e falha sem o brilho da trilha).
  - **Profundidade estática e execução legível** (pedido depois de olhar a HUD na borda direita:
    "muito flat, tudo chapado"). A captura mostrava arcos de 2,5dp em cor sólida, trilha a 10% × 1,6
    quase invisível, miolo vazio e o cometa de 1,5dp sumindo contra o notch. Agora, sem depender da
    política de movimento: brilho do tom sob cada arco (12%, dobro do traço), **ponta acesa** — rampa a
    branco nos últimos 70° e um ponto de luz no fim do valor, nunca além dele, nenhum em arco cheio —,
    **poço radial** no miolo atrás da marca (estático; o teste de bitmap que afirma o miolo igual com e
    sem sessão continua passando) e trilha a 2,2×. O cometa passou a 0,8 do traço, ganhou a **pista**
    da órbita a 14% (lê "gira" mesmo parado) e um halo radial na cabeça com miolo branco. O halo manda
    no alcance: `appUsageRingOrbitReach` foi de 3,4dp para 5,3dp, dentro dos 8dp do respiro e dos 6dp
    de meio vão — `HudNotchGeometryTest` afirma. Intensidades medidas em bitmap nos dois temas: com 16%
    de brilho e 35% na ponta o laranja do tema claro desbotava para bege.
  - **A órbita é por fora para a marca não encolher** (E11). Por dentro do último arco de cota ela
    comia o miolo, e a marca da conta trabalhando caía de 14dp para 8dp — justo a conta que merecia
    atenção ficava com o ícone menor. Ela passa `appUsageRingOrbitReach` (3,4dp, contando a cabeça do
    cometa) além dos 44dp do anel,
    fora dos limites do `Canvas`, e cabe no respiro de 8dp do notch e na metade do vão de 12dp entre
    anéis — `HudNotchGeometryTest` afirma as duas coisas.
  - **O Codex tem sonda própria** (`LocalCodexActivityDataSource`, E10): o índice de sessões é só do
    Claude CLI, e uma execução do Codex nunca acendia o arco. Primeiro o estado do **app desktop**,
    `thread_turns.status = 'inProgress'` em `~/.codex/thread_history_1.sqlite`, vivo com item nos
    últimos 10 min ou iniciado há menos de 2 (a guarda do Codenotch contra turno preso depois de uma
    queda); depois o **rollout** escrito nos últimos 5 min, para o CLI. A ordem é medida: com um turno
    do app rodando havia sete minutos, o rollout não era escrito desde o início dele — a data do
    arquivo sozinha diria "parado". Só leitura, só metadados; hoje e ontem de `sessions/`, nunca o
    histórico inteiro. Leitura que falha mantém o veredito anterior.
- **O que a barra de linhas ensinou e continua valendo**: o balão é conteúdo da janela, nunca `Popup`
  (popup aqui é camada **dentro** da janela e saía recortado sobre o próprio alvo); a HUD não tem
  translucidez própria (a opacidade é só a preferência do usuário); cota sem projeção continua na HUD
  (o percentual é fato medido); nenhum formato novo — percentual de `compactPercentageLabel`, reset de
  `resetShortLabel`, rótulo curto de `hudQuotaShortLabel`.
- **Opacidade no Windows: piso de 55% e repintura depois da troca** (`hudWindowOpacityPercent`,
  `hudRepaintsAfterOpacityChange`). A janela transparente do Compose no Windows só recebe o mouse
  onde o fundo tem alfa 1/255 (`JLayeredPaneWithTransparencyHack`), e o sistema multiplica esse alfa
  pela opacidade. Relato: com a opacidade em 50% e de volta a 100%, a HUD perdia o hover até sair e
  voltar ao modo. Medido com uma sonda (janela transparente igual à da HUD, `Robot` sobre ela,
  eventos AWT contados): mudar a opacidade refaz a camada **sem** esse fundo — 100 → 50 → 100 sem
  repintar dá zero eventos, repintando volta; e 50% nunca recebe o mouse, nem repintando
  (1 × 127/255 arredonda para zero), enquanto 55% a 99% recebem. Os dois ajustes são só do Windows
  (#340); no Linux e no macOS a preferência vale inteira e sem repintura.
- **Indicador Gargantua** (`AppGargantuaRing`, `GargantuaDrawing.kt`, `GargantuaQuotaArc.kt`,
  `AppGargantuaTokens`; 2026-09-28). O anel passou de 44dp para 64dp com o buraco negro de
  Interestelar no miolo. A primeira versão tinha disco de filamentos finos que sumia atrás da marca e
  arcos de cota em cor chapada. A segunda
  (disco de seis faixas com Doppler, arcos com gradiente e ponta incandescente) foi recusada pelo
  usuário: disco chamativo demais, atrapalhando a leitura do indicador, e arcos ainda planos. Três
  direções foram prototipadas em HTML (tubo iluminado, órbitas 3D inclinadas, vidro com plasma) e
  a escolhida foi **vidro com plasma**: a trilha é um tubo de vidro com reflexos vindos do alto à
  esquerda (`GargantuaQuotaArc.kt`), a cota é plasma no tom semântico dentro dele, e o cenário
  ficou discreto — sombra em 62% do miolo, anel de fótons, lente fina e uma faixa de disco
  translúcida com Doppler (token `ember`). Tudo cabe no miolo livre; o disco não atravessa os
  arcos. **Decisão:** a regra anterior era "arco imóvel pixel a pixel"; agora três pulsos de luz
  correm no plasma em `flowMillis` (2,8s) e se apagam perto das pontas.
- **Nascimento e colapso** (`HudPresence`, `mergeHudPresence`, `rememberHudPresence`,
  `GargantuaTransition.kt`; 2026-09-28). Pedido: ativar uma API mostra um buraco negro surgindo e
  desativar some com um colapso. Prototipados em HTML (3 nascimentos, 4 colapsos); escolhidos
  **S1 · onda de choque** (1,1s) e **C2 · colapso com clarão** (480ms). Depois o pedido cresceu: o
  início do app e a abertura da HUD também nascem, em cascata de 140ms entre contas.
  **Decisão de geometria:** a janela continua saindo de `hudNotchSizes`, sem redimensionamento por
  quadro. A conta que nasce já ocupa o espaço e se revela nele; a que sai fica na lista marcada
  `LEAVING`, **no mesmo lugar**, até o colapso acabar, e só então o notch encolhe num passo só. Os
  itens da faixa são compostos com `key(targetKey)`, senão a conta que colapsa no meio herdaria o
  estado da vizinha. Presença é enum próprio (não valor novo em enum existente). "Reduzir
  animações" entrega a lista viva, sem transição. O invariante passou a
  ser o **comprimento**: `GargantuaHudTest` exige pixels iguais entre quadros além do fim do arco e
  nenhuma luz em cota zerada. Sem `continuous && !reduced` não há fluxo.

## Fora do alcance dos testes

Os testes de componente desenham numa cena fora de tela, sem janela AWT e sem gerenciador de
janelas; os de geometria provam a conta, não o que o sistema faz com ela. O que depende do sistema
de janelas só se sabe **medindo na máquina** — como o Codenotch escreve no `TASKS.md` o que o render
fora de tela não enxerga. Esta é a lista a conferir quando um host de janela muda ("Platform
reality" no `CONTRIBUTING.md`, #342). Célula vazia é **não medido**, não "funciona".

| Comportamento | Windows 11 | Linux | macOS |
|---|---|---|---|
| Recorte da janela parada (`Window.shape`) deixa o clique passar e corta a pintura | medido (C11, #294) | quebrado no elementary 6.1/Gala (X11): o `shape = null` não tira o recorte e o balão sai cortado (#340) — desligado | |
| Clique em pixel transparente é engolido pela janela | medido (C11) | provável no X11, com a janela sem recorte (#340) — custo não medido | |
| Janela transparente que muda de origem mostra quadro antigo | medido (#294, 39–49 de ~668 quadros) | | |
| `alwaysOnTop` perde para a barra de tarefas *topmost* | medido (#288) | | |
| Monitor sob o ponteiro e encaixe na área útil | medido (#273, #288) | | |
| Escalas diferentes por monitor | só em máquina real | | |
| Maximizar zera o recorte dos cantos (`DesktopWindowFrame`, `shape = null`) | em uso desde maio | risco da #340, sem relato | |

Ao medir um item, preencha a célula com a data, a máquina e a issue, no mesmo commit da mudança.
