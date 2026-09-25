# HUD: encaixe nas bordas direita/inferior, barra de tarefas e emoji por conta — issues #288 e #287

Base: `8c77a26 feat(hud): show each window's percentage in the notch (#286) (#289)`.

Duas issues sobre o mesmo notch. **A #288 vai primeiro**: é defeito, e as duas mexem em
`HudNotch.kt`/`HudWindow.kt`. Fazendo o emoji antes, cada captura de validação da #288 teria
de ser refeita.

## 1. Diagnóstico

### #288 — espaço sobrando à direita e embaixo

Evidência (4ª foto da issue): notch vertical na borda direita, **alças visíveis** (estado
aberto), e um vão até a borda da tela de ~3,3× a largura do notch. Isso bate com
`HUD_BALLOON_GAP + HUD_BALLOON_WIDTH` = 274dp. O vão tem exatamente o tamanho do balão.

A geometria pura está certa. `hudWindowBounds` cola a janela na borda, e `HudNotch` põe o
notch em `width - acrossSize` (`HudNotch.kt:478`). O vão só aparece se a janela AWT ficar
**com a origem da janela aberta e o tamanho da parada**:

```
aberta:  x = direita − (notch + 10 + 264 + 16)   largura = notch + 290
parada:  x = direita − (notch + 16)               largura = notch + 16
defeito: x da aberta + largura da parada  → notch colado na borda de uma janela que termina 274dp antes da tela
```

Isso só acontece embaixo e à direita, porque só ali a origem muda entre parada e aberta. O
KDoc de `hudRestWindowBounds` já avisa: *"embaixo e à direita … a origem anda na espessura"*.
Em cima e à esquerda a origem é fixa e o defeito não aparece, e é isso que a issue relata.

**Hipótese H1 — derrubada em A01.** A suspeita era o par tamanho/posição chegar dessincronizado
ao AWT ao abrir e fechar. Uma sonda com janela real (transparente, sem moldura, `alwaysOnTop`)
alternando parada/aberta na borda direita, com intervalos de 0 a 400 ms e em escala 1,0 e 1,25,
terminou sempre com os limites pedidos.

**Causa medida.** As fotos mostram o cursor de mover **longe** do notch: o defeito é do arrasto, não
do repouso. Só dá para pegar a mão com o notch aberto, e o gesto de arrasto guarda os lambdas da
composição em que começou. Neles `windowSize` é o tamanho da janela **aberta**, e `dragTo` o usava
em `fitWindowPosition`: o primeiro passo prendia à tela uma janela da largura do balão e a
empurrava 274dp para dentro. Como o passo é incremental, o vão seguia o arrasto inteiro. O encaixe
lia o centro da mesma largura errada e soltava o notch na borda errada (na medição, soltar à
direita o levou para o topo). Em cima e à esquerda a janela aberta não é recuada, e por isso só
embaixo e à direita.

### #288 — barra de tarefas cobrindo a HUD

Isso foi uma **decisão**, não um descuido. O plano da #256 (`hud-arrasto-taskbar-256-execucao.md`)
passou a usar os limites físicos do monitor *"permitindo que ela ocupe a região da barra de
tarefas"*, e o CLAUDE.md registra *"pode ficar sobre a barra de tarefas"*. `resolveHudScreenArea`
e `pointerScreen()` usam `ScreenInfo.bounds`, nunca `workArea`.

O problema é que o `alwaysOnTop` não ganha da barra do Windows. Ela também é *topmost*, e volta
para cima de toda janela *topmost* a cada clique, hover ou notificação. Na 2ª foto o notch de
baixo fica metade atrás dela. Não existe API suportada para vencer essa disputa, então a saída é
não disputar: **encaixar na área útil** (`ScreenInfo.workArea`, que já é calculada com
`Toolkit.getScreenInsets`).

### #287 — emoji por conta

Duas contas Claude no mesmo notch mostram o mesmo asterisco. A cor por conta (#275) pinta a
marca do miolo, mas no 1º print as duas contas estão sem cor escolhida. Mesmo com cor, ela só
separa as contas para quem lembra qual tom é de qual conta. O pedido é um emoji no **canto
superior direito do anel** (3º print).

## 2. Decisões propostas

| # | Decisão | Por quê |
|---|---|---|
| D1 | O arrasto parte de `hudDragWindowBounds` (a janela de arrasto já encaixada) e mede por `dragSize`, derivado da geometria, nunca por `windowSize`. | A medida vem de `sizes`, que não muda durante o arrasto, então o lambda guardado pelo gesto continua certo. *(Substitui a proposta original de `setBounds` atômico, que atacava a H1.)* |
| D2 | A HUD em repouso e aberta fica na **área útil** (`workArea`). O **arrasto** continua livre sobre a tela inteira (`bounds`), e o encaixe ao soltar usa a área útil. | Desfaz a decisão da #256 com a evidência que ela não tinha: a barra ganha a disputa de *topmost*. O arrasto livre continua passando por cima da barra sem prender o ponteiro. |
| D3 | A identidade do monitor gravada (`hudScreenId`/`hudScreenBounds`) continua sendo `bounds`. | A área útil muda quando a barra é movida ou redimensionada, e com ela como chave o `resolveScreen` perderia o monitor. |
| D4 | Barra de tarefas com ocultação automática fica fora do escopo. | Os insets dela são zero. Quando ela sobe, cobre o notch, e isso vem da escolha do usuário de ocultá-la. Vai documentado, sem ser escondido. |
| D5 | O emoji sai de um **conjunto fixo** (enum `AccountEmoji`, ~16 opções + "Nenhum"), e não de um campo livre. | É o mesmo raciocínio da paleta fixa de cores: cada glifo é medido nas fontes de emoji dos três SOs, ocupa um grafema só e tem largura previsível. Um campo livre traria sequências ZWJ, tons de pele, bandeiras e o risco de o glifo virar quadrado vazio sem fonte. **Pendente de confirmação do usuário**, ver §5. |
| D6 | O emoji é gravado no nó do perfil (`emoji` em `AnthropicProfileRegistry`), pelo **nome** do enum, como a `color`. Nome desconhecido vira "Nenhum". | Renomear um valor apaga a escolha de quem o tinha, a mesma regra de `AccountAccent`. |
| D7 | O emoji é **selo** e não muda `hudNotchSizes`: fica sobre o canto do anel de 36dp, passando no máximo 4dp para fora, dentro do respiro de 8dp do notch e do vão de 6dp até o texto. | A geometria é dona do tamanho e não mede nada. O selo não pode abrir um segundo caminho de tamanho. `HudNotchGeometryTest` afirma o limite. |
| D8 | Superfícies: selo no anel (faixa completa **e** compacta), ao lado do título no cabeçalho do balão, na linha do perfil em Configurações → Contas e no título do card do dashboard. | A cor da #275 já ocupa esses mesmos quatro lugares. Identidade que aparece na HUD e some no card faria o usuário procurar a conta pelo emoji e não achar. A tooltip da bandeja fica de fora, porque o `szTip` tem 127 caracteres e o emoji ocupa dois UTF-16. |
| D9 | O selo é decorativo na semântica, e o nome da conta continua escrito. | Segue a regra de `AppProviderMark`: identificação não é o único portador do nome. |
| D10 | O CLAUDE.md e o design system registram a exceção: o emoji é **conteúdo escolhido pelo usuário**, como o apelido, e não cromo. | O readme do design system diz que o emoji do tema *"era o único emoji da interface"*. Sem o registro, o próximo passe de conformidade apagaria o selo. |

## 3. Atividades

Uma atividade por commit, com código, teste e documentação da mesma decisão juntos.

### #288

| ID | Atividade | Arquivos | Validação |
|---|---|---|---|
| A01 | **Medição antes de mexer.** Registro opt-in (`USAGE_MONITOR_DEBUG_HUD=1` → `~/.usage-monitor/diagnostics/hud-bounds.jsonl`) com os limites pedidos, o `window.bounds` real depois de aplicar, a borda, o estado (parada/aberta/arrasto), `bounds`/`workArea`/insets e a escala. Reproduzir na borda direita e embaixo: parar, passar o ponteiro, arrastar e soltar com o ponteiro sobre a mão. Confirmar ou derrubar H1. Aproveitar para ver o retângulo escuro atrás do notch durante o arrasto (1ª foto). | `HudWindow.kt`, novo `HudBoundsRecorder.kt` | Registro real anexado à tabela. Se H1 cair, **parar e replanejar A02**. |
| A02 | **Arrasto pela janela de arrasto** (reescrita depois de A01 derrubar a H1). O arrasto parte de `hudDragWindowBounds` e prende à tela pela medida dela (`dragSize`), não pelo `windowSize` que o gesto congelou no estado aberto. | `HudWindow.kt`, `HudNotchGeometry.kt` | Teste puro: nas quatro bordas, o notch fica no mesmo ponto parado, aberto e no começo do arrasto. Manual no app real: arrastar pela mão à direita e embaixo. |
| A03 | **Área útil.** `resolveHudScreenArea` e o encaixe de `dragFinish` passam a usar `workArea`; `fitWindowPosition` do arrasto segue com `bounds`. O `maxAlong` da faixa compacta também sai da área útil. | `HudWindow.kt`, `HudNotchGeometry.kt` (KDoc) | `nearestHudPlacement`/`hudWindowBounds` com área útil deslocada (barra embaixo, à esquerda e em cima): o notch encosta na barra e não passa por baixo dela. Manual: barra embaixo; clicar na barra e conferir que o notch continua visível. |
| A04 | **Documentação.** No CLAUDE.md (Barra HUD → Posição): trocar "pode ficar sobre a barra de tarefas" pela regra nova e pelo motivo; registrar D4. No protótipo, o estado "notch acima da barra" em `§HUD`. Nota de reversão no plano da #256. Tabela deste plano. | `CLAUDE.md`, `docs/planos/prototipo-visual-opencode.html`, `hud-arrasto-taskbar-256-execucao.md` | Revisão do diff. |
| A05 | Suíte e validação final da #288. | — | `gradlew.bat allTests`, `git diff --check`, e as 4 fotos da issue refeitas nas mesmas posições. |

### #287

| ID | Atividade | Arquivos | Validação |
|---|---|---|---|
| E01 | **Spike de renderização.** Os ~16 candidatos num `Text` do Compose Desktop sobre o notch: Windows (Segoe UI Emoji), Linux (Noto Color Emoji; sem ela, o que aparece?), macOS no job `build-macos`. Também em densidade 1,15/1,25 e no `ScreenshotGenerator` offscreen. Sai daqui a lista final e a decisão sobre fallback: sem fonte colorida, mostrar o glifo monocromático ou nada. | scratch, fora do repo | Captura por SO anexada. Glifo que vira quadrado vazio em algum SO **sai da lista**. |
| E02 | **Domínio e persistência.** Enum `AccountEmoji` (nome estável + `glyph`) em `presentation/ui/theme/`, ao lado de `AccountAccents.kt`, com `fromStorage`. `AnthropicProfileRecord.emoji` + `KEY_EMOJI` + `setEmoji`. `accountEmojisOf(records)` em `Main.kt`, no mesmo desenho de `accountColorsOf`. | `AccountEmoji.kt`, `AnthropicProfileRegistry.kt`, `Main.kt` | Teste do registro: grava, lê, remove e trata nome desconhecido como nulo. `main()` não ganha estado novo: o mapa sai do mesmo fluxo de perfis que já alimenta as cores (limite do backend JVM). |
| E03 | **Configurações → Contas.** Seletor ao lado do de cor na linha do perfil. Se nenhuma primitiva servir (`AppSwatchChip` pinta cor, não glifo), nasce `AppGlyphChip` no mesmo commit, com `components/…/AppGlyphChip.prompt.md` e a entrada no índice do readme do design system. | `SettingsDialogContent.kt`, `AppControls.kt`, `docs/design-system/` | `ComponentTest`: escolher o emoji dispara `onAnthropicProfileEmojiChange(id, emoji)`, e "Nenhum" envia nulo. Kit `.jsx` da aba Contas atualizado. |
| E04 | **Modelo da HUD.** `HudAccount.accountEmoji: AccountEmoji?`, preenchido em `buildHudAccounts` por `profileId`. Perfil nulo não recebe emoji, porque conta nula não é "todas as contas". A cadeia do notch não ganha parâmetro novo: o dado viaja dentro do `HudAccount`, como `accountAccent`. | `HudModel.kt`, `HudWindow.kt` | `HudModelTest`: emoji por perfil, ausente sem escolha, ausente para fonte que não é Anthropic. |
| E05 | **Selo no anel e no balão.** `HudAccountBadge` sobreposto ao canto superior direito do anel, nas faixas completa e compacta e nas quatro bordas (o selo não gira: fica de pé, como o texto do balão). O cabeçalho do balão mostra o emoji entre a marca e o título. `hudBalloonHeight` não muda, porque a linha do cabeçalho tem altura fixa. | `HudNotch.kt`, `HudBalloon.kt`, `HudNotchGeometry.kt` (constantes do selo) | `HudNotchTest`: selo presente só na conta com emoji e tamanho do notch idêntico com e sem ele, nas quatro bordas. `HudNotchGeometryTest`: o selo cabe no respiro e no vão até o texto, e não invade o anel vizinho. `HudNotchTextFitTest` segue verde de 100% a 200%. |
| E06 | **Card do dashboard.** Emoji antes do título no cabeçalho da `ApiUsageCard`, cortado junto com o título quando falta largura. | `ApiUsageCard.kt`, `ApiUsageCardFormatting.kt`, `DashboardScreen.kt` | `ComponentTest`: o título continua localizável por texto com o emoji presente, e o card estreito (230dp) não quebra linha. |
| E07 | **Documentação.** No CLAUDE.md (Sistema visual → "Cor por conta"): parágrafo irmão "Emoji por conta", com D5–D10. No readme do design system, a regra de conteúdo: emoji só como conteúdo do usuário. No protótipo, o selo no mockup do notch e o seletor na aba Contas. Na Ajuda, o passo de ativação no tópico da HUD ou de contas, citando o **rótulo real** do controle. | `CLAUDE.md`, `docs/design-system/readme.md`, protótipo, catálogo da Ajuda | O teste da Ajuda (todo tópico tem PT/EN e passos) segue verde. |
| E08 | Suíte final e capturas. `ScreenshotFixtures` com emoji em uma das duas contas Claude, **só** se E01 mostrar que o gerador renderiza emoji offscreen; se não mostrar, a captura fica sem ele e o motivo vai registrado aqui. | `ScreenshotFixtures.kt` | `gradlew.bat allTests`; `gradlew.bat generateHelpMedia` se o tópico da Ajuda mudar. |

## 4. Riscos

- **H1 pode estar errada.** Se A01 mostrar que o vão vem de outra causa (DPI misto, `resizable = false`
  recusando o tamanho, ou o `LaunchedEffect` do hover reabrindo com a área antiga), A02 muda. A
  medição vem antes do conserto justamente por isso.
- **`setBounds` e a janela transparente.** O Compose pode sobrescrever a geometria na recomposição
  seguinte, se o `WindowState` e o AWT discordarem. A sonda de A02 existe para pegar exatamente isso.
- **A área útil tira a HUD da faixa da barra.** Quem gostava do notch sobre a barra perde essa
  opção. Hoje ela só funciona até o primeiro clique na barra, então a perda é de algo que já não
  funcionava.
- **Emoji no Linux sem fonte colorida.** Se E01 mostrar que o glifo vira quadrado vazio, a saída
  é o fallback decidido lá. Não se embute fonte de emoji: a Noto Color Emoji tem ~10 MB.
- **Um sétimo sinal no anel.** O miolo já tem marca, arcos de cota, órbita de sessão ativa e pulso
  de atenção. O selo mora no canto, fora do miolo, e não pisca nem gira. Nenhum movimento contínuo
  novo.

## 5. Decisões pendentes com o usuário

1. **D5, conjunto fixo × campo livre.** A recomendação é o conjunto fixo. Com campo livre, E02 e E03
   crescem: validação de um grafema só, medida de largura real e fallback por glifo.
2. **D8, o emoji também no card do dashboard (E06)?** Recomendado. Pode ficar só na HUD se o
   usuário preferir.

## 6. Pontos de situação

| ID | Atividade | Commit / comando | Resultado |
|---|---|---|---|
| — | Plano redigido a partir das issues, das 7 capturas e de `HudWindow.kt`/`HudNotchGeometry.kt`/`HudNotch.kt` | — | concluída |
| A01 | Medição | Sonda `awaitApplication` (descartada, fora do repositório): 12 e 40 alternâncias parada/aberta, escalas 1,0 e 1,25 → `window.bounds` igual ao pedido em todas. App real com `-Duser.home` isolado e ponteiro sintético (`SetCursorPos`/`mouse_event`): arrastar pela mão na borda direita deixou a mão ~270px à esquerda do ponteiro, e soltar levou o notch ao topo | H1 derrubada, causa real encontrada |
| A02 | Arrasto pela janela de arrasto | `hudDragWindowBounds` + `dragSize` em `HudWindow.kt`; teste `comecar o arrasto nao tira o notch do lugar em nenhuma borda`. App real: mão sob o ponteiro no meio do arrasto, e soltar devolve o notch rente à direita | concluída |
| A03 | Área útil | `resolveHudScreenArea`/`dragFinish` com `workArea`, `fullScreenAreaDp` removida; teste `o notch encosta na barra de tarefas e nao passa por baixo dela`. App real, tela 1366×768 com barra de 48px: notch de baixo parado e aberto termina em y=720, rente à barra | concluída |
| A04 | Documentação | CLAUDE.md (Barra HUD: arrasto e área útil), nota de reversão no plano da #256. O protótipo já dizia *"o encaixe de baixo para logo acima dela"* (#164) e não muda | concluída |
| A05 | Suíte | `gradlew.bat allTests` → BUILD SUCCESSFUL em 7m19s | concluída |
