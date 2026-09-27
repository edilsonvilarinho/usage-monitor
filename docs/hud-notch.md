# Barra HUD — notch: decisões e histórico

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

**Barra HUD — notch** (`HudWindow.kt` + `HudNotch.kt` + `HudBalloon.kt` + `HudHandles.kt` +
`HudNotchGeometry.kt` + `HudModel.kt` + `AppShellActions.kt` + `CardActions.kt` + `AppUsageRing` +
`HudModePreferences.kt` + `HudWindowPreferences.kt`; issue #164, redesenhada no plano
[`profundidade-movimento-hud-notch-execucao.md`](planos/profundidade-movimento-hud-notch-execucao.md)):
terceiro chrome, ainda mais discreto que o modo somente cards. A janela principal fica **escondida**
(`visible = !hudMode`), com a geometria intacta, e sobra um **notch colado numa borda da tela** numa
janela própria, transparente, sem decoração e sempre no topo (`HudWindowHost`). O desenho vem do
Codenotch; a regra de conteúdo vem das seis versões da barra de linhas que ele substituiu.
**Não é valor novo em enum existente**: `hudMode` continua um booleano, exclusivo com o modo somente
cards por regra dos setters em `AppShellState.kt`, e `HudEdge` é enum novo.
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
    `onSurface`; a cor de risco fica no arco e na palavra, senão ela informaria o estado sozinha. Conta
    de cota única continua com o número em `labelMedium`, sem rótulo. O preço é a espessura: cada
    janela é uma linha `labelSmall` de 14dp (`HUD_STRIP_LINE`), e o notch de cima vai de 36dp para 42dp
    com duas e 56dp com três. **O foco continua** (`HudAccount.focusIndex`/`focusLine`) no pulso de
    atenção, na célula compacta e na bandeja, onde não cabe uma linha por anel. A palavra continua
    sendo a do **pior** risco da conta: com as janelas à vista ela resume a conta, não um número.
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
  (a cunha do `TooltipTail` do Codenotch) aponta para o anel, e trocar de anel desliza o balão pela
  mola `GENTLE` com crossfade do conteúdo.
- **HUD padrão na instalação nova** (`markHudDefaultPendingOnFreshInstall` + `hudDefaultShouldSwitch`;
  issue #277). **Não é o default da leitura**: `readPersistedHudMode` continua `false`, e quem já usa o
  app nunca é arrastado para a HUD. Instalação nova é `hudMode` e `windowPlacement` ausentes e nenhum
  recibo de atualização (a regra de `ReleaseNotesDecision`), lida **antes** de o coletor da janela
  gravar qualquer coisa. Nesse caso o app grava o modo padrão e marca `hudDefaultPending`. A troca sai
  na primeira coleta com alguma conta e **sem janela modal aberta**: na primeira execução quem está
  aberta é Configurações, e a instalação nova sobe sem API habilitada — abrir direto no notch mostraria
  "Carregando" para sempre. Ela manda uma notificação, uma vez só, com os três caminhos de volta.
  **Mora no bloco da bandeja**, porque a bandeja é um desses caminhos: sem ela o app não troca
  sozinho. Qualquer escolha de modo antes da troca apaga a pendência, porque a escolha do usuário
  vence. E sem API habilitada o notch diz "Nenhuma API" em vez de "Carregando" (`hudFallbackLabel`).
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
  As alças e o balão entram **deslizando de dentro do notch**, com fade e escala pela mola `GENTLE`.
- **Identificação, como no Codenotch e no ai-usagebar**: a **marca do fornecedor** (`AppProviderMark`)
  no miolo de cada anel, na cor do texto — em volta dela os arcos já carregam a cor de risco —, e no
  cabeçalho do balão no acento da fonte. O rótulo da conta é o **título do card**
  (`ApiUsageStats.displayTitle`, dono único: "Anthropic — Padrão"); a HUD mostrava só "Padrão" e
  escondia de quem era a conta. O **plano** ("Max 20x", "ChatGPT Plus") vem no rodapé do balão e na
  descrição do anel. O anel passou de 28 para 36dp para a marca caber no miolo.
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
    recorte: a área do balão volta a engolir clique, mas o notch não pisca. **macOS e Linux não foram
    medidos.** `HudHitRegionApplier` guarda o último retângulo porque `Window.getShape()` devolve cópia
    em `Path2D`, que nunca é igual ao pedido.
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
  caixa de cada conta; fora dos anéis nada acontece), com o anel "pressionado" enquanto coleta; botão
  direito vai direto a "Somente cards" (sem popup — seria recortado dentro desta janela). No corpo o
  gesto é `draggable = false`: passar do limiar só desiste do clique, e **mover é só pela mão**, que usa o
  mesmo gesto com arrasto. A ação de cada anel é **declarada** na semântica, não instalada por `clickable`, que
  consumiria o `down`. Nenhuma coordenada sai do composable: o host lê o ponteiro na tela por
  `MouseInfo`, incremental. Saídas para a janela padrão: "Padrão" no balão da engrenagem, bandeja,
  `Ctrl+Shift+H`; "Abrir" da bandeja e a segunda instância saem da HUD antes de ativar a janela.
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
- **Sessão ativa e atenção são movimento contínuo, atrás da política**: o arco fino que gira **em
  órbita por fora** do anel (turno CLI nos últimos 5 min, `SessionPulseViewModel.activeTargets`) e o pulso do
  anel de fora em `Atenção`/`Crítico` só existem com `AppMotionPolicy.continuous`. Sem ela o arco
  fica parado e o pulso some; a palavra continua dizendo o estado.
  - **A órbita é por fora para a marca não encolher** (E11). Por dentro do último arco de cota ela
    comia o miolo, e a marca da conta trabalhando caía de 14dp para 8dp — justo a conta que merecia
    atenção ficava com o ícone menor. Ela passa `appUsageRingOrbitReach` (3dp) além dos 36dp do anel,
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
