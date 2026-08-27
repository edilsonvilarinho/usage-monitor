# Conformidade com o design system — plano de execução

> Fonte de verdade desta iniciativa. Rastreio no GitHub: issue [**#117**](https://github.com/edilsonvilarinho/usage-monitor/issues/117).
>
> Quem retomar a iniciativa lê o **Ponto de situação** primeiro e não precisa de mais nada.
> Cada atividade é um commit atômico, e a linha do *Registro de execução* entra **no mesmo commit**
> da atividade que ela descreve — em commit separado a linha pode existir sem a mudança, e o
> registro deixa de servir para auditoria.

---

## Ponto de situação

**Estado atual:** `Concluída. As treze atividades estão na main, publicadas. Falta a aceitação visual do usuário`
**Última atualização:** 2026-08-27
**Branch:** `main`

### ▶ Atividade corrente

**Nenhuma.** A execução terminou.

### ⏭ Próxima atividade

**Nenhuma dentro deste plano.** O que resta é decisão e verificação do usuário:

1. **Rodar `gradlew.bat run`** em modo normal e em modo somente cards, nas duas línguas, olhando
   hover, foco, arrasto de card, redimensionamento, o botão de fechar em vermelho e a faixa de hover
   do topo. Nenhuma das onze atividades de código foi olhada na janela real — ver *Fora de escopo*,
   item 0.
2. **Decidir sobre os oito débitos** de *Fora de escopo*. O item 1, os acentos congelados na variante
   escura, é o único que produz um defeito observável hoje: no tema claro eles dão 2,64:1 contra a
   superfície.

A issue [#117](https://github.com/edilsonvilarinho/usage-monitor/issues/117) **fica aberta** até a
aceitação visual. Fechá-la agora afirmaria que a passada foi vista funcionando, e ela não foi.

---

## Decisões travadas

Tomadas com o usuário em 2026-08-27, antes de a execução começar.

| # | Decisão | Consequência |
|---|---|---|
| D1 | O design system é **normativo** para token, primitiva, copy e iconografia | O protótipo continua normativo para o mockup de cada tela; Compose é implementação. Tabela de precedência no `CLAUDE.md` |
| D2 | O escopo é **adoção de primitiva por tela** | Contraste, código morto e presets não medidos ficam como débito registrado, não corrigido — ver *Fora de escopo* |
| D3 | Rastreio em **uma issue mestre + este doc** | Sem sub-issue por tela: mais lugares para o estado divergir |

---

## Levantamento — reaberto na A12 com o número real

O levantamento original contou os débitos por `grep` de `Surface(`, `Card(`, `Modifier.border`,
`.background(` e `RoundedCornerShape(`. Ele mede o **tamanho da suspeita**, não o do débito: gráfico,
amostra de cor e previsualizador de tema desenham retângulo à mão porque é assim que o design system
manda desenhá-los. As ocorrências O5, O6 e O7 são as três vezes em que isso apareceu.

A coluna **Contado** é o número da A01; **Real** é o que a leitura dos call sites mostrou.

| # | Superfície | Contado | Real | Convertido | Não convertível | Contado por engano |
|---|---|---|---|---|---|---|
| 1 | Dashboard | 0 | 0 | — | — | — |
| 2 | Card de uso | 6 | 6 | 6 | 0 | 0 |
| 3 | Faixa de atualização | 2 | 2 | 2 | 0 | 0 |
| 4 | Histórico | 0 | 0 | — | — | — |
| 5 | Sessões CLI | 5 | 2 | 1 | 1 | 3 |
| 5b | Sessões CLI — Resumo | 0 | 0 | — | — | — |
| 6 | Uso do time | 5 | 5 | 3 | 2 | 0 |
| 7 | Presença do time | 2 | 3 | 2 | 1 | 0 |
| 8 | Chaves das contas | 2 | 2 | 2 | 0 | 0 |
| 9 | Configurações | 6 | 1 | 1 | 4 | 1 |
| 10 | Notas da versão | 0 | 0 | — | — | — |
| 11 | Cromo das janelas | 5 | 5 | 4 | 1 | 0 |
| — | Estados de tela | — | 22 | 22 | 0 | 0 |
| — | Bolhas de tooltip | — | 4 | 4 | 0 | 0 |
| — | `DepthSurface` | — | 4 | 4 | 0 | 0 |
| | **Total** | **33** | **56** | **51** | **9** | **4** |

O total **subiu**, não desceu: quatro itens foram contados por engano e nove não são convertíveis,
mas trinta apareceram fora do `grep` original — os 22 estados de tela que uma função só desenhava
(O7), as quatro bolhas de tooltip e os quatro usos de `DepthSurface`, que o levantamento tinha
listado como "primitiva duplicada" sem contar os call sites.

**Adoção das primitivas, antes e depois:**

| Primitiva | A01 | Agora |
|---|---|---|
| `AppEmptyState` | 0 telas | 9 pontos |
| `AppLoadingState` | 1 tela | 7 pontos |
| `AppErrorState` | 1 tela | 6 pontos |
| `AppWindowScaffold` | 3 de 7 janelas | 4 de 7 |
| `AppTooltipSurface` | não existia | 4 |
| `AppStatusIndicator` | 5 arquivos | 7 |
| `DepthSurface` | 3 telas | **removida** |
| `Card` do Material | 1 | **0** |
| `Color(0x…)` fora de `theme/` | 2 (em `desktopMain`) | **0** |
| `HorizontalDivider` do Material | 2 | **0** |

---

## Progresso por atividade

| Atividade | Superfície | Estado | Commit |
|---|---|---|---|
| A00 — Regra de precedência no `CLAUDE.md` + skill | — | ✅ | `6ed23fd` |
| A01 — Plano e issue de rastreio | — | ✅ | `df73cd2` |
| A02 — Uso do time | 6 | ✅ | `176bcb8` |
| A03 — Primitiva duplicada `DepthSurface` | 5, 7, 8 | ✅ | `7039daa` |
| A04 — Presença e chaves das contas | 6, 7, 8 | ✅ | `2307760` |
| A05 — Sessões CLI | 5 | ✅ | `a6ff0d0` |
| A06 — Card de uso | 2 | ✅ | `53b4943` |
| A07 — Faixa de atualização | 3 | ✅ | `fd3ac5c` |
| A08 — Configurações | 9 | ✅ | `0127909` |
| A09 — Tooltips | 4 arquivos | ✅ | `b92b7eb` |
| A10 — Vazio, carregando e erro | 22 pontos | ✅ | `632cde9` |
| A11 — Cromo das janelas | 11 | ✅ | `b912e9e` |
| A12 — Protótipo, kit e capturas | — | ✅ | `305bf48` |
| A13 — Fechamento | — | ✅ | *(este commit)* |

Legenda: ⬜ pendente · 🟡 em andamento · ✅ concluída · ⛔ bloqueada

---

## Detalhe das atividades

### A02 — Uso do time · `TeamUsageScreen.kt` — ✅

Três dos cinco débitos foram fechados; **dois não são convertíveis** e viraram registro. Ver a
ocorrência O1: a leitura dos call sites derrubou duas das três primitivas que este plano tinha
nomeado a partir da contagem por `grep`.

Feito:

- **`Modifier.appNestedGroupItem(indent)` criada** em `AppStructure.kt` e adotada nos dois blocos
  aninhados (`:650` cabeçalho de colunas, `:687` cada sessão). Ela carrega os três passos que os dois
  sites repetiam por extenso: `fillMaxWidth`, fundo em `surface` e `appNestedGroupGuide` no recuo.
  **Não é `AppDataSurfaceFlush`**: aquele recorta, arredonda e desenha borda em volta do que envolve,
  e aplicado por item de `LazyColumn` poria uma caixa em volta de **cada** sessão e cortaria a guia
  de 2dp, que só fica contínua porque a lista não tem vão entre itens.
- **`TeamHealthCell` → `AppStatusIndicator`.** `healthTone` já existia (`CliSessionsScreen.kt`) e as
  cores batem exatamente: `AppTone.CRITICAL` resolve para `colorScheme.error`, que é `#E86A6A` no
  escuro e `#B3261E` no claro — os mesmos valores de `accents.saturated`. O parâmetro `showLabel`
  saiu: os **três** chamadores passavam `false`, e o ramo `true` era código morto.
- A escada de três superfícies sobreviveu: faixa da conta em `surfaceVariant`, linha do integrante
  transparente, bloco aninhado em `surface`.

Não convertido, com o motivo:

- **`TeamAccountGroupHeader` (`:840`)** carrega sete colunas alinhadas ao mesmo x do `AppDataRow` da
  linha do integrante — é essa comparação que a faixa existe para permitir. `AppSectionHeader` é
  título, subtítulo e ações; ele não tem onde pôr colunas. Converter quebraria o alinhamento.
- **`TeamAccountUuidHeader` (`:997`)** precisa de `start = TEAM_ROW_HORIZONTAL_PADDING + indent`, e o
  padding de `AppSectionHeader` é interno e fixo. Só passaria a caber se a primitiva ganhasse um
  parâmetro de recuo — decisão de sistema, não de tela, e por isso fora desta atividade.

Verificado: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamUsageScreenTest" --tests
"com.usagemonitor.ui.TeamPresenceScreenTest"` → **83 testes, 0 falhas**. As duas suítes juntas porque
`TeamHealthCell` é `internal` e a tela de presença a consome: o parâmetro removido obriga as duas a
mudarem no mesmo commit, ou o commit não compila sozinho.

### A03 — Primitiva duplicada `DepthSurface` — ✅

`DepthSurface` era a mesma superfície de `AppDataSurface` — mesmo `clip`, mesmo fundo `surface`,
mesma borda de 1dp, mesmo `padding` — com duas diferenças: um `Surface` a mais em volta, só para
carregar `shadowElevation`, e nenhum `verticalArrangement`. **Os quatro chamadores estavam em
elevação zero**, três pelo default e um passando `AppElevation.card` explicitamente, então o
`Surface` externo não desenhava nada em lugar nenhum.

- `AppDataSurface` ganhou `verticalArrangement`, com default `spacedBy(AppSpacing.sm)` — o
  comportamento de quem já a usava não muda. Um parâmetro resolve o que uma segunda função resolvia.
- Os quatro call sites passaram a `AppDataSurface(..., verticalArrangement = Arrangement.Top)`, que
  preserva exatamente o espaçamento de hoje: `TeamPresenceHeader`, `TeamKeyCard`,
  `AdvancedDisclosure` e `SessionMetadataCard` separam os filhos com `Spacer` próprio, e o `spacedBy`
  default somaria 8dp a cada um.
- `DepthSurface.kt` removido. Nenhuma referência restou.

Esta atividade **não** existia com esta forma no plano original: ela era um item dentro da A03 e da
A04. Ver a ocorrência O2.

Verificado: `gradlew.bat desktopTest --tests TeamPresenceScreenTest --tests CliSessionsScreenTest
--tests AppStructureTest --tests TeamKeysAdminScreenTest --tests TeamAdminSectionTest` →
**92 testes, 0 falhas**.

### A04 — Presença e chaves das contas — ✅

**`AppSectionHeader` não era a primitiva certa, e o parâmetro de recuo não era o que faltava.** Ler
as duas sub-faixas mostrou que elas falam **baixo** de propósito: rótulo em `labelSmall` sobre
`onSurfaceVariant`. `AppSectionHeader` fala alto — `titleSmall` sobre `onSurface`, altura mínima de
barra. Adotá-lo inverteria a escada: a sub-faixa passaria a gritar mais que a faixa de conta que a
cobre. Ver a ocorrência O4.

Feito:

- **`AppGroupBand` criada** em `AppStructure.kt`: rótulo quieto, `detail` opcional, `indent` que
  **soma** ao `horizontalPadding` da lista, ação opcional à direita e a divisória própria — como a de
  `AppDataRow`, que é o que dispensa vão entre itens. Registrada no design system em
  `components/data/AppGroupBand.{prompt.md,jsx,d.ts}` e indexada no `readme.md`, conforme a regra que
  entrou no `CLAUDE.md` na A00.
- **Adotada nas duas telas**: `TeamAccountUuidHeader` (`TeamUsageScreen`), que era o item deixado
  pendente na A02, e `TeamPresenceAccountSubgroupHeader` (`TeamPresenceScreen`), que ainda carregava
  o botão de apagar conta — agora no `trailing`.
- **`TeamKeysList` → `AppWindowScaffold`.** O corpo era `Column(fillMaxSize).padding(16.dp)` com
  `spacedBy(12.dp)`: exatamente o que a primitiva faz, com os dois valores escritos como literal em
  vez de saírem de `AppSpacing.lg` e `AppSpacing.md`. A raiz `Surface(background)` de `:83` **fica** —
  ela é o padrão compartilhado das janelas (`CliSessionsScreen`, `TeamUsageScreen` e
  `TeamPresenceScreen` têm a mesma), e o que faltava era o scaffold dentro dela, não no lugar dela.
- **Dois testes novos** em `AppStructureTest`: o recuo soma ao padding, e a faixa mostra rótulo,
  detalhe e ação.

Não convertido, com o motivo:

- **`TeamPresenceEmailHeader` (`:683`)** é irmã de `TeamAccountGroupHeader`: mesma faixa de conta com
  colunas alinhadas ao `AppDataRow` do integrante. Mesma conclusão da O1.

Verificado: `gradlew.bat desktopTest` sobre `TeamUsageScreenTest`, `TeamPresenceScreenTest`,
`TeamKeysAdminScreenTest`, `TeamAdminSectionTest` e `AppStructureTest` → **101 testes, 0 falhas**.

### A05 — Sessões CLI · `CliSessionsScreen.kt` — ✅

**Três dos cinco débitos que o levantamento contou aqui não são débitos.** Ver a ocorrência O5.

Feito:

- **`LiveBadge` → `AppStatusIndicator`.** Ponto e palavra, com o tom saindo de `AppTone.OK`. Ele
  pintava o ponto e o texto com `CACHE_READ_COLOR`, que é `darkAppAccents.cacheRead` congelado num
  `val` de topo de arquivo: resolvido uma vez por processo, sem ler o tema em vigor. No tema claro
  aquele verde dá **2,64:1** contra a `surface`. A primitiva o troca por
  `AppAccents.current.cacheRead`, que passa nos dois — a correção sai de graça com a adoção, e vale
  para os três chamadores (`CliSessionsScreen`, `TeamPresenceScreen`, `TeamUsageScreen`).

Não é débito, com o motivo:

- **`CostDistributionBar`, os segmentos dela e a amostra da legenda** são uma **barra de composição
  empilhada**, que as fundações do design system listam entre os gráficos permitidos — "line charts,
  bar series, a per-hour activity heatmap, stacked composition bars — all drawn in accent colors on
  `--raised`", e `surfaceVariant` é `--raised`. `AppProgressTrack` é a barra de **cota**: uma fração,
  um tom. Forçá-la aqui perderia as quatro faixas.
- **O anel de 1dp do `HelpDot`** não cai na armadilha nº 6. Aquela é sobre caixa de 4dp: a 110% o
  anel vira 2px de cada lado e cobre o trilho inteiro. Aqui a caixa tem 14dp (~15px na mesma escala)
  e sobram ~11px para o `?`. Medido, não estimado.
- **`GlossaryPanel` já era conforme**: `AppDataSurfaceFlush` + `AppSectionHeader` + `AppDataRow`. O
  `.border` que o levantamento atribuiu a ele é do `HelpDot`, o vizinho de cima no arquivo.

Verificado: `gradlew.bat desktopTest --tests CliSessionsScreenTest --tests TeamPresenceScreenTest
--tests TeamUsageScreenTest` → 45 + 31 + 52 = **128 testes, 0 falhas**. As três porque `LiveBadge` é
`internal` e as três telas a consomem.

### A06 — Card de uso · `ApiUsageCard.kt` — ✅

Os seis débitos eram **um** débito repetido: `clip` + `background` + `border` de 1dp escritos por
extenso em seis pontos, cada um com o próprio `padding` e o próprio alinhamento.

- **`Modifier.appSurfaceBlock(shape, color)` criada** em `AppStructure.kt`: o mesmo trio que
  `AppDataSurface` aplica, separado do contêiner para os casos em que o layout já existe e só a
  superfície falta. Não é contêiner porque os seis sites têm `padding` e arranjo diferentes — foi
  a mesma leitura que produziu `appNestedGroupItem` na A02.
- **O `color` é parâmetro** porque o hover deste sistema **é** troca de superfície neutra
  (`surface` → `surfaceVariant`), e porque o bloco interno mora em `surfaceVariant` enquanto o painel
  mora em `surface`. Não é porta para acento: acento vive no marcador de 2dp e na linha do gráfico.
- **A primitiva não inclui sombra.** Elevação é de janela, diálogo, menu e overlay. Quem a pede aqui
  é o card **enquanto está sendo arrastado** — nesse instante ele é overlay de fato —, e pede com um
  `Modifier.shadow` explícito no ponto de uso, que com `cardElevation` em zero não desenha nada.
- **O último `Card()` do Material saiu da aplicação.** A raiz do card virou `Box` com
  `appSurfaceBlock(shape = AppShapes.medium, color = hoverBackground)`, preservando o arrasto por
  pressão longa, o `animateContentSize` da minimização e o `graphicsLayer` da animação de entrada.
  `BorderStroke` e `CardDefaults` saíram dos imports junto.
- Os outros cinco sites: badge de estado da fonte (com `Color.Transparent`, que é o único sem fundo),
  bloco de "nenhum uso free detectado", linha de modelo do OpenCode/Kilo, botão de ação do cabeçalho
  (cor variável, acesa só quando o semáforo está aceso) e badge de cota do card minimizado.

Não convertido, com o motivo:

- **`CardIconActionButton` continua não sendo `AppIconButton`.** Ele carrega o semáforo de sessão:
  `rememberSessionPulseFrame`, cor de contêiner que acende com a severidade e uma
  `contentDescription` montada com o motivo do pisca. `AppIconButton` tem `tone`, não pulso. Colocar
  o semáforo dentro da primitiva a faria conhecer `SessionPulse`, que é domínio de sessão CLI e não
  de controle. A superfície dele, essa, passou a sair de `appSurfaceBlock`.
- **O anel de 1dp segue sendo `Modifier.border` nos seis.** A armadilha nº 6 é sobre caixa de 4dp,
  onde o anel arredondado para cima cobre o trilho inteiro; aqui as caixas são contêineres de texto
  e ícone. Medição igual à da O5.

Verificado: `gradlew.bat desktopTest --tests ComponentTest --tests AppThemeScaleTest --tests
AppStructureTest` → 75 + 2 + 7 = **84 testes, 0 falhas**. O `ComponentTest` é onde vivem os ~30 casos
do card, e é ele que responde pela troca do `Card` por `Box`.

### A07 — Faixa de atualização · `DashboardScreenWarnings.kt` — ✅

O `AppUpdateBanner` montava um `Surface` com `BorderStroke` e desenhava o próprio marcador de 2dp —
uma cópia linha a linha do que o `AppBanner` já traz. Agora é o `AppBanner`, com o rótulo da ação no
slot `action`.

- **Os quatro estados do contrato foram conferidos antes da troca**
  (`docs/design-system/components/shell/AppUpdateStrip.prompt.md`): `Available` abre a release,
  `Downloading` **não tem ação** — faixa clicável sem rótulo seria alvo de clique invisível —,
  `Ready` reinicia e instala, e `Failed` abre a release. O caminho manual do `Failed` é o que o
  contrato exige: SmartScreen ou antivírus bloqueando o `Setup.exe` sem assinatura é desfecho
  esperado, não exceção.
- **`AppBanner` passou a truncar o título em uma linha**, como o `AppSectionHeader` já fazia. Era a
  única diferença real entre os dois: a faixa tinha `maxLines = 1` e a primitiva não, e sem isso um
  título longo quebraria para uma segunda linha e empurraria os cards do dashboard para baixo — que é
  exatamente o defeito da issue #67. A mudança vale também para os outros dois chamadores da
  primitiva (`SessionHealthBanner` e `PersistentApiWarningBanner`).
- A faixa continua **sem descrição**, e é isso que a mantém com um terço da altura de um aviso de
  duas linhas: a descrição só repetiria em prosa o que o rótulo da ação diz.
- `BorderStroke`, `Surface`, `AppElevation`, `AppShapes` e mais cinco imports saíram do arquivo.

Verificado: `gradlew.bat desktopTest --tests AppUpdateBannerTest --tests AppStatesTest --tests
ComponentTest --tests CliSessionsScreenTest` → 8 + 7 + 75 + 45 = **135 testes, 0 falhas**. As duas
últimas porque a mudança no título do `AppBanner` alcança os outros chamadores dele.

### A08 — Configurações · `SettingsDialogContent.kt` — ✅

**Um dos seis débitos era débito.** Ver a ocorrência O6.

Feito:

- **`AppSettingsNav` saiu da tela e virou primitiva publicada.** O design system já publicava o
  componente (`components/shell/AppSettingsNav.prompt.md`); o Kotlin o tinha como duas composables
  `private` dentro do diálogo, com o nome errado (`SettingsSideNav` / `SettingsNavItem`). Agora o
  nome é o mesmo dos dois lados.
- Ela reaproveita `AppTab` como item — rótulo mais a `testTag` que a suíte observa —, porque um
  segundo tipo para os mesmos dois campos só daria duas coisas para manter em sincronia.
- **Irmã do `AppTabs`, não uma variante dele**: aba troca o que a tela mostra, numa faixa horizontal
  sublinhada; o trilho troca a seção de uma janela alta, numa coluna que **não rola**. Largura fixa
  porque item selecionado não pode mudar a largura do trilho, ou a lista inteira se mexe a cada
  clique — e é isso que o teste novo mede, em pixels.
- **O item selecionado ganha `surfaceVariant` sem borda**, e por isso ele **não** usa
  `Modifier.appSurfaceBlock`, que sempre desenha o anel: um anel em volta de cada item transformaria
  o trilho numa pilha de caixas.

Não é débito, com o motivo:

- **`Surface(background)` da raiz** é o padrão compartilhado das janelas, o mesmo de
  `CliSessionsScreen`, `TeamUsageScreen`, `TeamPresenceScreen` e `TeamKeysAdminScreen`. Mesma
  conclusão da A04.
- **A coluna de conteúdo** já usa `AppSpacing.lg` de padding e `AppSpacing.md` de vão — os defaults
  do `AppWindowScaffold` —, mas é um `verticalScroll` dentro de um `Box` que também hospeda a barra
  de rolagem. O scaffold é uma `Column` com `weight`; não cabe, e forçá-lo tiraria a barra de dentro
  da área rolável, que é onde ela precisa estar para não cobrir o trilho.
- **`ThemePresetCard` e as três amostras de cor** pintam as cores do **preset**, não as do tema em
  vigor — é o que um previsualizador de dezesseis temas faz. É a mesma categoria do gráfico da O5: o
  único lugar em que pintar cor arbitrária é o comportamento certo.
- **`AppSliderThumb`** fica, como o plano já previa: o design system especifica trilha de 4dp e
  polegar de 12dp com os slots do `Slider` do Material, para a semântica de progresso continuar
  vindo dele.
- **O `HorizontalDivider` do Material não existe neste arquivo.** `grep -c` devolve zero. O
  levantamento o contou por engano.

Verificado: `gradlew.bat desktopTest --tests AppStructureTest --tests ComponentTest --tests
AutoUpdateToggleTest --tests ThemePresetPickerTest` → 8 + 75 + 17 + 3 = **103 testes, 0 falhas**.

### A09 — Tooltips — ✅

**`AppTooltip` não era a primitiva que faltava.** Ele é um `TooltipBox` inteiro com uma bolha de
texto simples dentro; as quatro bolhas carregam listas de métrica e são posicionadas por
`HoverTooltipBox` e pelos gráficos. O que as cinco repetiam era a **anatomia da bolha**, não o
comportamento de hover.

- **`AppTooltipSurface` criada** em `AppControls.kt`: superfície `surfaceVariant`, raio 6, borda de
  1dp e 2dp de overlay curto — `tonalElevation` **e** `shadowElevation` no mesmo patamar, senão o tom
  sobe sem a sombra acompanhar e a bolha lê como bloco chapado. Oito é elevação de diálogo e de
  menu, que cobrem a janela; a bolha cobre um ponto do gráfico.
- **Só o conteúdo é do chamador**, e é por isso que é superfície e não contêiner: cada bolha tem o
  próprio `padding` e a própria largura máxima (280dp no card, 230dp no histórico,
  `TOOLTIP_WIDTH` no gráfico de turnos).
- **Quatro call sites**: o próprio `AppTooltip`, `UsageTooltipContent`, `ChartTooltip`
  (`TurnSeriesChart`) e `HistoryTooltipBubble` (`UsageHistoryLineChart`). Sete imports saíram dos três
  arquivos de gráfico.
- Era essa repetição que deixava "duas tooltips sobre o mesmo tipo de gráfico flutuando em alturas
  diferentes" acontecer — o comentário estava escrito em dois dos arquivos, cada um corrigindo o
  outro à mão.

Não convertido, com o motivo:

- **`HistoryAnnotationLabel`** continua com `Surface` próprio. Ele não é bolha: é um rótulo **chapado**
  de anotação sobre o eixo, com raio 4 e **sem** elevação nenhuma. Passá-lo pela superfície de
  tooltip o faria flutuar sobre o gráfico que ele anota.
- **`AppTooltip` continua com zero chamadas.** Ele agora compartilha a bolha, mas nenhuma tela o usa —
  as que precisam de tooltip usam `HoverTooltipBox`, que é persistente e aceita métricas. E o design
  system **não publica componente de tooltip nenhum**: não há `AppTooltip.prompt.md`. Está no débito.

Verificado: `gradlew.bat desktopTest --tests ComponentTest --tests CliSessionsScreenTest --tests
AppControlsTest` → 75 + 45 + 9 = **129 testes, 0 falhas**.

### A10 — Vazio, carregando e erro — ✅

Não eram nove estados vazios. Eram **vinte e dois estados de três tipos diferentes** desenhados pela
mesma função. Ver a ocorrência O7.

`CenteredMessage` era uma frase centrada em `bodyMedium`, e as cinco telas a chamavam para *carregando*,
para *erro* e para *vazio* — os três estados que o design system separa em três desenhos, com três
significados. Ela saiu; os vinte e dois pontos passaram a chamar a primitiva do estado que descrevem:

| Estado | Primitiva | Pontos |
|---|---|---|
| Carregando | `AppLoadingState` — esqueleto **estático**, sem shimmer | 7 |
| Erro | `AppErrorState` — indicador crítico com ponto e palavra | 6 |
| Vazio | `AppEmptyState` — frase centrada, sem ilustração e sem ação inventada | 9 |

- **`CliUsageBreakdownPane` juntava dois estados numa frase só** com `errorMessage ?: loading`. Eles
  não são a mesma coisa: "ainda não chegou" é carregando e "não deu para ler" é erro. Viraram um
  `if`.
- **A copy não foi tocada**, como o plano mandava: ela já estava centralizada nos arquivos de
  formatação e já obedecia à regra do design system de nomear o recorte ("nesta janela", "neste
  projeto"). O que mudou é o desenho.
- O erro passou a ter ponto **e** palavra, porque `AppErrorState` é construído sobre
  `AppStatusIndicator`. Antes o erro era indistinguível de "carregando" e de "vazio": três frases
  cinzas no mesmo lugar.

Verificado: `gradlew.bat allTests` → **1468 testes, 0 falhas** em 135 classes. Foi a passada mais
larga do plano, e tinha de ser: a função saiu de cinco arquivos ao mesmo tempo.

### A11 — Cromo das janelas · `DesktopWindowFrame.kt` — ✅

O arquivo de maior risco do plano, e o único sem nenhum teste. A suíte foi escrita **antes** de o
código mudar.

Feito:

- **`AppChrome` criado** em `AppTheme.kt`, com os cinco patamares que `tokens/spacing.css` publica:
  `titleBar` 34, `toolbar` 34, `statusBar` 30, `control` 28, `updateStrip` 28. Eles existiam como
  **três `private val` em dois arquivos de componente mais um literal `34.dp`** na moldura do
  desktop — quatro donos para um valor que o sistema define uma vez. `AppStructure`, `AppControls` e
  `DesktopWindowFrame` passaram a ler dali.
- **As duas divisórias do cromo eram `HorizontalDivider` do Material com meia opacidade**
  (`outlineVariant.copy(alpha = 0.5f)`). O sistema tem uma divisória só, de 1dp em `outlineVariant`,
  e a moldura da janela não é exceção. Viraram `AppDivider`.
- **A cor de hover do botão de fechar era um literal**, `Color(0xFFC62828)`, em dois pontos. Era o
  único `Color(0x…)` fora de `theme/` em toda a aplicação — o `grep` da A01 não o pegou porque
  varreu `commonMain`, e a moldura vive em `desktopMain`. Agora é `colorScheme.error`, que é `#E86A6A`
  no escuro e `#B3261E` no claro. O design system pede exatamente isso: o botão de fechar é a **única**
  exceção de hover do sistema, e ele preenche `--crit`.
- **`DesktopWindowFrameTest` criado**, quatro casos: o botão preenche a altura da barra menos a
  divisória, o glifo que não se explica carrega descrição semântica, o clique despacha, e as cinco
  alturas batem com o contrato do design system.

Não convertido, com o motivo:

- **`TitleBarButton` continua não sendo `AppIconButton`.** Ele é um retângulo de 40 × 33dp que
  preenche a altura da barra; `AppIconButton` tem 26dp **e borda**. O botão arredondado flutuando
  dentro da barra era o único lugar do app onde um controle não encostava na própria moldura, e um
  anel de 1dp em volta de cada botão de janela seria pior. A decisão está no comentário do arquivo
  desde a refatoração de agosto; o design system diz que `AppIconButton` serve o cromo, mas ele diz
  também que o botão de fechar é exceção — e é a exceção que descreve esta barra.
- **O resto do arquivo não é alcançável pelo harness.** `DesktopWindowFrame`, `DesktopTitleBar`,
  `DesktopDialogTitleBar` e `CompactTitleBarOverlay` são todos `WindowScope.`, porque o
  `WindowDraggableArea` precisa de uma janela AWT real, e `runDesktopComposeUiTest` não fornece uma.
  O que sobra é o botão, que é onde as três decisões do cromo moram.

Verificado: `gradlew.bat allTests` → **1472 testes, 0 falhas** (1468 + os 4 novos).

⚠ **Falta a verificação que só a máquina do usuário faz**: `gradlew.bat run` em modo normal e em modo
somente cards, olhando hover, arrasto da janela, o botão de fechar em vermelho e a faixa de hover do
topo. A captura offscreen do `generateScreenshots` não desenha moldura de janela.

### A12 — Protótipo, kit e capturas — ✅

- **Capturas regeneradas**: `gradlew.bat generateScreenshots` produziu as 13 cenas, e **9 mudaram** —
  exatamente as telas tocadas. `history`, `settings`, `theme-presets` e `settings-team` saíram byte a
  byte iguais, e a de Configurações é o sinal que importa: a extração do `AppSettingsNav` foi
  pixel-idêntica.
- **`dashboard.png` e `presence-accounts.png` conferidos a olho**, que são as duas trocas de maior
  risco: o card sem o `Card()` do Material e a sub-faixa de conta pela `AppGroupBand`.
- **Protótipo**: a lede de §2 deixou de dizer "dezessete"; um painel novo lista as seis primitivas da
  passada, com o motivo de cada uma; §4b ganhou a nota dos três estados de tela; e §15 recebeu cinco
  linhas — a verificação pendente na janela real, as duas decisões de não converter e os dois riscos
  de contraste.
- **Design system**: `readme.md` ganhou a seção *The conformance pass*, com a tabela das seis
  primitivas e o parágrafo do que **não** foi convertido; `github.md` registra a sincronia, desta vez
  lendo o Kotlin direto — o que o `readme.md` original declarava não ter feito.
- **Os `.jsx` do kit não mudaram**, e é o resultado certo: a passada foi de adoção, não de redesenho.
  Onde o desenho mudou de fato — os três estados de tela — a mudança está descrita no protótipo, que
  é onde o kit não chega (ele recria oito telas, e nenhuma delas é um estado de carregamento).

### A13 — Fechamento — ✅

As doze atividades de execução estão na `main`, publicadas. O que a passada entregou:

**Regra.** O `CLAUDE.md` carrega a tabela de precedência — design system para token, primitiva, copy
e iconografia; protótipo para o mockup de cada tela; Compose é implementação —, a regra de que
nenhuma tela reimplementa primitiva, a regra de que acento sai de `AppAccents.current` e nunca de
`darkAppAccents`, e a obrigação de registrar toda alteração de tela nos dois documentos no mesmo
commit. A skill do design system virou `/usage-monitor-design`.

**Seis primitivas**, cada uma nascida de uma repetição medida: `Modifier.appNestedGroupItem`,
`AppGroupBand`, `Modifier.appSurfaceBlock`, `AppSettingsNav`, `AppTooltipSurface` e `AppChrome`. As
três que têm contraparte no design system foram registradas lá no mesmo commit que as criou.

**Quatro coisas deixaram de existir no produto:** o último `Card()` do Material, a primitiva
duplicada `DepthSurface`, o `HorizontalDivider` do Material e o último `Color(0x…)` fora de `theme/`.

**Cinquenta e um pontos convertidos**, nove registrados como não convertíveis com o motivo, e quatro
que o `grep` do levantamento tinha contado por engano.

**A suíte cresceu de 1462 para 1472 testes**, todos verdes, e ganhou a primeira cobertura do cromo
das janelas — um arquivo que não tinha nenhuma.

**As sete ocorrências adversas estão registradas**, e a lição que atravessa cinco delas é uma só:
contagem por `grep` mede o tamanho da suspeita, não o do débito, e anatomia parecida não é a mesma
primitiva. Cada atividade passou a ler os call sites antes de aceitar a primitiva que o plano
nomeava, e foi assim que três primitivas nasceram no lugar de três conversões erradas.

**O que falta é a verificação que só a máquina do usuário faz.** Ver *Fora de escopo*, item 0.

---

## Registro de execução

Uma linha por commit, em ordem cronológica. Cada entrada carrega o comando que rodou e o resultado,
nunca a intenção.

| # | Commit | Atividade | O que foi feito | Verificação |
|---|---|---|---|---|
| 1 | `6ed23fd` | A00 | Tabela de precedência, regra de não reimplementar primitiva e regra de acento no `CLAUDE.md`; skill do design system registrada em `.claude/skills/usage-monitor-design/` | Diff inspecionado — mudança só de documentação, sem código |
| 2 | `df73cd2` | A01 | Este plano, com o levantamento das 13 superfícies e a medição de adoção por primitiva; issue de rastreio [#117](https://github.com/edilsonvilarinho/usage-monitor/issues/117) criada com o mesmo ponto de situação | `gh issue create` devolveu `issues/117`; contagens conferidas por `grep` sobre `presentation/ui/` |
| 3 | `176bcb8` | A02 | `Modifier.appNestedGroupItem` criada e adotada nos dois blocos aninhados; `TeamHealthCell` passou a ser `AppStatusIndicator` e perdeu o parâmetro `showLabel`, cujo ramo `true` era morto; o chamador da tela de presença acompanhou | `gradlew.bat desktopTest --tests TeamUsageScreenTest --tests TeamPresenceScreenTest` → 52 + 31 = **83 testes, 0 falhas** |
| 4 | `7039daa` | A03 | `AppDataSurface` ganhou `verticalArrangement`; os quatro call sites de `DepthSurface` migraram com `Arrangement.Top`; `DepthSurface.kt` removido | `gradlew.bat desktopTest` sobre `TeamPresenceScreenTest`, `CliSessionsScreenTest`, `AppStructureTest`, `TeamKeysAdminScreenTest` e `TeamAdminSectionTest` → 31 + 45 + 5 + 5 + 6 = **92 testes, 0 falhas** |
| 5 | `2307760` | A04 | `AppGroupBand` criada, registrada no design system e adotada nas duas sub-faixas de conta; `TeamKeysList` passou a `AppWindowScaffold`; dois testes novos de primitiva | `gradlew.bat desktopTest` sobre `TeamUsageScreenTest`, `TeamPresenceScreenTest`, `TeamKeysAdminScreenTest`, `TeamAdminSectionTest` e `AppStructureTest` → 52 + 31 + 5 + 6 + 7 = **101 testes, 0 falhas** |
| 6 | `a6ff0d0` | A05 | `LiveBadge` passou a `AppStatusIndicator`, o que também tira o acento congelado no escuro dos três chamadores; barra de composição, `HelpDot` e `GlossaryPanel` reavaliados e retirados da conta de débito | `gradlew.bat desktopTest --tests CliSessionsScreenTest --tests TeamPresenceScreenTest --tests TeamUsageScreenTest` → 45 + 31 + 52 = **128 testes, 0 falhas** |
| 7 | `53b4943` | A06 | `Modifier.appSurfaceBlock` criada e adotada nos seis pontos do card; o último `Card()` do Material saiu da aplicação, com `BorderStroke` e `CardDefaults` junto | `gradlew.bat desktopTest --tests ComponentTest --tests AppThemeScaleTest --tests AppStructureTest` → 75 + 2 + 7 = **84 testes, 0 falhas** |
| 8 | `fd3ac5c` | A07 | `AppUpdateBanner` passou a ser `AppBanner`; a primitiva ganhou título de uma linha, que era a única diferença real entre as duas | `gradlew.bat desktopTest --tests AppUpdateBannerTest --tests AppStatesTest --tests ComponentTest --tests CliSessionsScreenTest` → 8 + 7 + 75 + 45 = **135 testes, 0 falhas** |
| 9 | — | fase 3 | Fechamento da fase do dashboard | `gradlew.bat allTests` → **BUILD SUCCESSFUL** em 1m30s |
| 10 | `0127909` | A08 | `AppSettingsNav` saiu de dentro do diálogo e virou primitiva publicada, com o nome que o design system já usava; um teste novo mede a largura fixa do trilho | `gradlew.bat desktopTest --tests AppStructureTest --tests ComponentTest --tests AutoUpdateToggleTest --tests ThemePresetPickerTest` → 8 + 75 + 17 + 3 = **103 testes, 0 falhas** |
| 11 | `b92b7eb` | A09 | `AppTooltipSurface` criada e adotada nas quatro bolhas, incluindo a do próprio `AppTooltip`; sete imports saíram dos três arquivos de gráfico | `gradlew.bat desktopTest --tests ComponentTest --tests CliSessionsScreenTest --tests AppControlsTest` → 75 + 45 + 9 = **129 testes, 0 falhas** |
| 12 | `632cde9` | A10 | `CenteredMessage` removida; os 22 pontos de cinco telas passaram a `AppLoadingState` (7), `AppErrorState` (6) e `AppEmptyState` (9) | `gradlew.bat allTests` → **1468 testes, 0 falhas** em 135 classes |
| 13 | `b912e9e` | A11 | `AppChrome` com os cinco patamares do cromo; as duas divisórias do Material viraram `AppDivider`; o literal `Color(0xFFC62828)` do botão de fechar virou `colorScheme.error`; `DesktopWindowFrameTest` criado | `gradlew.bat allTests` → **1472 testes, 0 falhas** |
| 14 | `305bf48` | A12 | 13 capturas regeneradas, 9 mudaram; protótipo e design system registram a passada; levantamento reaberto com o número real | `gradlew.bat generateScreenshots` → 13 cenas; `dashboard.png` e `presence-accounts.png` conferidos a olho |
| 15 | *(este commit)* | A13 | Ponto de situação final; a issue #117 fica aberta até a aceitação visual | `gradlew.bat allTests` na A11 → **1472 testes, 0 falhas**. A A12 e a A13 não tocam em código |

---

## Ocorrências adversas

**O1 · 2026-08-27 · A02 — o mapeamento primitiva↔call site deste plano estava errado em dois de
três itens.**

O levantamento contou os débitos por `grep` de `Surface(`, `Card(`, `.background(` e
`Modifier.border`, e atribuiu a primitiva pelo *nome* do que a tela desenha. Ler os call sites
mostrou que duas das três atribuições não se sustentam:

- `AppDataSurfaceFlush` recorta, arredonda e desenha borda **em volta** do que envolve. Os dois
  blocos aninhados de `TeamUsageScreen` são itens irmãos de uma `LazyColumn` — têm de ser, porque
  aninhar lista em lista quebra a rolagem —, e a primitiva aplicada por item poria uma caixa
  arredondada em volta de cada sessão e cortaria a guia de 2dp, que só fica contínua porque a lista
  não tem vão. **Resolvido criando a primitiva que faltava**, `Modifier.appNestedGroupItem`, em vez
  de forçar a existente.
- `AppSectionHeader` é título + subtítulo + ações, com padding interno fixo. As duas faixas de conta
  carregam sete colunas alinhadas ao `AppDataRow` do integrante e um recuo por nível. **Não
  convertidas**, com o motivo registrado no detalhe da A02.

**O2 · 2026-08-27 · A03 — a fatia por tela não cabe numa primitiva que atravessa telas.**

`DepthSurface` aparecia como um item dentro da A03 (Presença) e outro dentro da A04 (Chaves), e o
levantamento não tinha visto os dois usos em `CliSessionsScreen`. Migrar a primitiva em três
commits deixaria a duplicata viva no meio do caminho e espalharia uma decisão só por três registros.
Ela virou **atividade própria**: a extensão de `AppDataSurface`, os quatro call sites e a remoção do
arquivo no mesmo commit. A A03 e a A04 foram redefinidas em volta disso, e a A04 passou a juntar
Presença e Chaves — as duas dependem da mesma extensão de `AppSectionHeader`.

**O7 · 2026-08-27 · A10 — o plano contou nove estados vazios; eram vinte e dois estados de três
tipos.**

A atividade estava escrita como "adotar `AppEmptyState` em nove estados vazios desenhados à mão". O
que existia era uma função só, `CenteredMessage`, chamada em **vinte e dois** pontos de cinco telas
para *carregando*, *erro* **e** *vazio* — os três estados que o design system separa em três
primitivas, justamente porque significam coisas diferentes: "está chegando", "não deu para ler" e
"não há nada aqui, e isso é um resultado legítimo". As três apareciam como a mesma frase cinza no
meio da tela.

Um dos pontos, no resumo por eixo, colapsava dois estados dentro de uma expressão
(`errorMessage ?: loading`) — a forma mais curta possível de dizer que a distinção tinha se perdido.

**Consequência para o resto do plano:** nenhuma. A atividade cobriu o que encontrou, e o
levantamento reaberto na A12 registra o número real.

**O6 · 2026-08-27 · A08 — cinco dos seis débitos das Configurações não eram débito, e um deles não
existia.**

A O5 avisou que o número por tela é teto. Nas Configurações o teto era seis e o real é um. A raiz
`Surface(background)` é o padrão de janela compartilhado; a coluna de conteúdo não cabe no
`AppWindowScaffold` porque hospeda a barra de rolagem ao lado; `ThemePresetCard` e as amostras
pintam as cores do preset previsto, não as do tema em vigor, que é a mesma categoria do gráfico da
O5; o `AppSliderThumb` é decisão registrada. E o **`HorizontalDivider` do Material simplesmente não
está no arquivo** — `grep -c` devolve zero.

O único débito real era de **nome**: o design system publica `AppSettingsNav` e o Kotlin tinha o
mesmo componente como duas composables `private` dentro do diálogo, com outro nome. Isso é o item 8
de *Fora de escopo* aparecendo dentro do escopo: quando a divergência de nome esconde uma primitiva
publicada que a tela reimplementou, ela deixa de ser questão de nomenclatura.

**Consequência para o resto do plano:** o levantamento é reaberto na A12, com o número por tela
corrigido para o que a leitura mostrou, e o total do débito passa a distinguir *convertido*,
*não convertível com motivo* e *contado por engano*.

**O5 · 2026-08-27 · A05 — a contagem por `grep` conta gráfico como retângulo pintado à mão.**

O levantamento marcou cinco débitos em `CliSessionsScreen`. Três não são: a barra de composição
empilhada, os segmentos dela e a amostra de cor da legenda **são** desenho à mão, e é assim que o
design system manda desenhá-los — gráfico é a única coisa que o sistema deixa pintar direto, em cor
de acento sobre `--raised`. O quarto, o anel do `HelpDot`, foi medido contra a armadilha nº 6 e passa:
aquela é sobre caixa de 4dp, esta tem 14. E o `GlossaryPanel` já usava as três primitivas certas — o
`.border` que o levantamento pendurou nele é do `HelpDot`, o vizinho de cima no arquivo.

**Consequência para o resto do plano:** o número de débitos por tela é **teto, não meta**.
`ApiUsageCard` (6), `SettingsDialogContent` (6) e o cromo das janelas (5) serão reavaliados um a um
antes de qualquer troca, e o que for gráfico ou já conforme sai da conta com o motivo escrito.

**O4 · 2026-08-27 · A04 — a primitiva certa não era a que tinha a anatomia parecida.**

A A04 tinha sido escrita para dar um parâmetro de recuo ao `AppSectionHeader`, porque a anatomia
batia: título, subtítulo, ação à direita. Batia mesmo — e ainda assim era a primitiva errada. As duas
sub-faixas de conta desenham o rótulo em `labelSmall` sobre `onSurfaceVariant`, e o
`AppSectionHeader` desenha em `titleSmall` sobre `onSurface`, com altura mínima de barra. A escada
de superfícies é uma hierarquia de **peso**, não de forma: adotar o cabeçalho de painel faria a
sub-faixa gritar mais que a faixa de conta que a cobre.

`AppGroupBand` nasceu daí — o degrau quieto, que o protótipo desenha e não nomeia. Ela foi
registrada no design system no mesmo commit, pela regra que entrou no `CLAUDE.md` na A00.

**Consequência para o resto do plano:** anatomia parecida não é a mesma primitiva. Antes de adotar,
comparar também **estilo de texto e cor** — é ali que mora a hierarquia. E cada atividade lê os call
sites antes de aceitar a primitiva que este documento nomeia; **débito que atravessa telas vira
atividade própria** em vez de item repetido em cada uma. A contagem por `grep` mede o tamanho do débito, não a solução dele. As
atividades A03 a A11 seguem com a primitiva sugerida, agora explicitamente como hipótese.

| Data | Atividade | O que aconteceu | Como foi resolvido |
|---|---|---|---|
| 2026-08-27 | A02 | O plano nomeava `AppDataSurfaceFlush` e `AppSectionHeader` para call sites que não as comportam (O1 acima) | Primitiva nova (`appNestedGroupItem`) para um caso; os outros dois registrados como não convertíveis, com o motivo |
| 2026-08-27 | A02 | `TeamHealthCell` tinha um ramo `showLabel = true` que nenhum dos três chamadores usava | Ramo removido junto com o parâmetro, na migração para `AppStatusIndicator` |
| 2026-08-27 | A03 | `DepthSurface` atravessava três telas e não cabia dentro de nenhuma atividade de tela (O2) | Virou atividade própria, com a extensão de `AppDataSurface` e os quatro call sites no mesmo commit |
| 2026-08-27 | A03 | O plano mandava rodar `--tests "com.usagemonitor.ui.TeamAdminUiTest"`, e **nenhum teste roda com esse filtro** | `TeamAdminUiTest.kt` é um arquivo com duas classes, `TeamAdminSectionTest` (6) e `TeamKeysAdminScreenTest` (5). O filtro do Gradle casa nome de classe, não de arquivo, e não falha quando outro filtro da mesma chamada casa. Os nomes certos estão na seção *Verificação* |
| 2026-08-27 | A04 | O plano queria dar um parâmetro de recuo ao `AppSectionHeader`; o problema não era o recuo, era o peso visual (O4) | `AppGroupBand`, primitiva própria para o degrau quieto da escada, registrada no design system |
| 2026-08-27 | A05 | Três dos cinco débitos contados em `CliSessionsScreen` não eram débito: gráfico permitido, anel medido e painel já conforme (O5) | Retirados da conta com o motivo escrito; o número por tela passa a ser teto, não meta |
| 2026-08-27 | A08 | Cinco dos seis débitos das Configurações não eram débito, e o `HorizontalDivider` do Material nem existe no arquivo (O6) | Reavaliados um a um; o único real era de nome, e virou a primitiva `AppSettingsNav` |
| 2026-08-27 | A10 | O plano contou nove estados vazios; eram 22 pontos de três tipos, todos pela mesma `CenteredMessage` (O7) | Os três tipos separados nas três primitivas do sistema; a função saiu |

---

## Fora de escopo — débito conhecido

Achados no levantamento de 2026-08-27, deixados de fora por D2. Registrados com a evidência para
poderem virar issue própria depois. **Não corrigir dentro desta iniciativa.**

0. **`gradlew.bat run` não foi executado.** Nenhuma das onze atividades foi olhada na janela real —
   hover, foco, arrasto de card, redimensionamento, o botão de fechar em vermelho, a faixa de hover
   do modo somente cards e as duas línguas. A captura offscreen do `generateScreenshots` não desenha
   moldura de janela, e o harness de componente não alcança nada que seja `WindowScope.`. **É a
   verificação que falta, e só a máquina do usuário a faz.**
1. **Acentos congelados na variante escura.** `CliSessionsScreen.kt:99-103` declara cinco `val` de
   topo de arquivo a partir de `darkAppAccents` (`INPUT_COLOR`, `OUTPUT_COLOR`, `CACHE_READ_COLOR`,
   `CACHE_WRITE_COLOR`, `SAVINGS_COLOR`), usados em 33 pontos do arquivo. `val` de topo de arquivo é
   resolvido uma vez por processo e não lê o tema em vigor: no tema claro, `#4CAF50` sobre `#FFFCFC`
   dá **2,64:1**, contra os 4,5:1 que `AppAccentsContrastTest` existe para garantir. Mesmo padrão em
   `ApiUsageCardFormatting.kt:132` (`accents: AppAccents = darkAppAccents` como default de
   parâmetro). A regra que fecha essa porta já entrou no `CLAUDE.md` na A00; o código não foi tocado.
2. **Código morto.** `ShimmerBox` tem **zero** call sites, e três comentários do repositório
   (`AppTheme.kt:35`, `AppStates.kt:257`, `DashboardScreen.kt:337`) ainda afirmam que ele é "a única
   animação infinita da app" — o design system proíbe shimmer explicitamente. `UsageArcChart` só é
   referenciado por `ComponentTest`. **`AppTooltip` continua com zero chamadas** mesmo depois da A09:
   as telas que precisam de tooltip usam `HoverTooltipBox`, que é persistente e aceita métricas, e o
   design system **não publica componente de tooltip nenhum**. Decidir remover ou justificar os três.
3. **`SettingsToast`.** `docs/design-system/components/feedback/AppBanner.prompt.md` afirma que o app
   não tem toast, porque um monitor de ciclo de 10 min não pode reportar falha com algo que
   desaparece. O toast das Configurações existe (`SettingsToast.kt`, consumido em
   `SettingsDialogContent.kt:191`).
4. **14 presets de tema nunca medidos.** `AppThemePresets.kt` tem 16 presets (8 escuros, 8 claros);
   `AppAccentsContrastTest` mede os acentos só contra `#1B1818` (Obsidiana) e `#FFFCFC` (Porcelana).
   Os outros 14 pares de superfície nunca foram medidos, e o design system afirma que os seis acentos
   de integração passam AA "em ambas as superfícies" — afirmação verdadeira para duas das dezesseis.
5. **`docs/design-system/uploads/` está vazio.** O `readme.md` cita
   `uploads/prototipo-visual-opencode.html` como a fonte de **todos** os valores do sistema.
6. **O kit cobre 8 das ~24 seções do protótipo.** Faltam, entre outras, Novidades da versão,
   Administração de chaves, Relatório PDF e a faixa de atualização.
7. **O design system se contradiz no casing da insígnia de estado.**
   `components/data/AppStatusIndicator.jsx` aplica `textTransform: uppercase` na palavra do estado;
   o `readme.md` do mesmo sistema diz "Sentence case for titles, tabs, buttons and prose. UPPERCASE
   only in mono-10 eyebrows and column headers" — e um estado não é nem eyebrow nem cabeçalho de
   coluna. O Kotlin segue a regra escrita (sentence case) e **não** foi alterado. Decidir de que lado
   corrigir.
8. **Nomes divergentes entre o design system e o Kotlin.** `AppWindowFrame` × `AppWindowScaffold`,
   `AppUpdateStrip` × `AppUpdateBanner`, `AppColumnHeader` × `AppColumnHeaderRow`, `AppPanel` ×
   `AppDataSurface`, `AppMetric` × `AppMetricBlock`, `AppSourceMark` × `AppSourceMarker`. Renomear de
   um lado ou do outro é decisão pendente; enquanto ela não vier, o mapeamento vive aqui.

---

## Verificação

**Nome de classe, não de arquivo.** O filtro `--tests` do Gradle casa a classe, e três arquivos de
teste deste repositório declaram mais de uma:

| Arquivo | Classes | @Test |
|---|---|---|
| `TeamAdminUiTest.kt` | `TeamAdminSectionTest` · `TeamKeysAdminScreenTest` | 6 · 5 |

Um filtro que não casa nada **não derruba a chamada** quando outro filtro da mesma linha casa: a
suíte passa em verde sem ter executado o que se queria executar.

| Quando | Comando |
|---|---|
| Por atividade, antes do commit | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.<ClasseDaTela>"` |
| Ao fim de cada fase | `gradlew.bat allTests` |
| Antes da A13 | `gradlew.bat allTests` · `gradlew.bat generateScreenshots` · `gradlew.bat run` |

`generateScreenshots` produz 12 cenas (`dashboard`, `history`, `settings`, `theme-presets`,
`settings-team`, `cli-sessions`, `cli-breakdown`, `cli-session-detail`, `team-trend`, `team-usage`,
`presence` claro e escuro, `presence-accounts`) com relógio manual e escala neutra de 100 — é a
comparação visual antes/depois de cada tela tocada. `gradlew.bat run` cobre o que a captura offscreen
não pega: hover, foco, arrasto de card, redimensionamento e o modo somente cards.

Riscos de teste já pagos uma vez neste repositório e que valem aqui:

- Tela que ficou mais alta obriga a subir a altura da **cena** (1024 × 768), nunca a do `Box` interno.
- Nenhuma animação infinita nova — ela trava o `waitForIdle`.
- `weight` dentro de `FlowRow` deixa o filho sem posicionar; o sintoma é `assertIsDisplayed` falhando
  com `boundsInRoot` válido.
- Ação que virou ícone precisa de `contentDescription` na semântica, não só de `onClickLabel`.
- Verde local e vermelho no CI em teste de UI: olhe o `~/.skiko` antes de olhar o teste.
