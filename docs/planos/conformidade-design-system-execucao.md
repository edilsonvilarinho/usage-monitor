# Conformidade com o design system — plano de execução

> Fonte de verdade desta iniciativa. Rastreio no GitHub: issue [**#117**](https://github.com/edilsonvilarinho/usage-monitor/issues/117).
>
> Quem retomar a iniciativa lê o **Ponto de situação** primeiro e não precisa de mais nada.
> Cada atividade é um commit atômico, e a linha do *Registro de execução* entra **no mesmo commit**
> da atividade que ela descreve — em commit separado a linha pode existir sem a mudança, e o
> registro deixa de servir para auditoria.

---

## Ponto de situação

**Estado atual:** `Em andamento — A00 a A03 concluídas`
**Última atualização:** 2026-08-27
**Branch:** `main`

### ▶ Atividade corrente

**A04 — Presença e chaves das contas.** As duas telas dependem da mesma extensão de primitiva:
`AppSectionHeader` precisa de um recuo para caber nas sub-faixas de conta (`TeamPresenceScreen:814`
e `TeamUsageScreen:997`, este último deixado em aberto na A02). Junto vai a raiz
`Surface(background)` de `TeamKeysAdminScreen` para `AppWindowScaffold`.

### ⏭ Próxima atividade

**A05 — Sessões CLI.** `CostDistributionBar`, `LiveBadge` e `GlossaryPanel`.

---

## Decisões travadas

Tomadas com o usuário em 2026-08-27, antes de a execução começar.

| # | Decisão | Consequência |
|---|---|---|
| D1 | O design system é **normativo** para token, primitiva, copy e iconografia | O protótipo continua normativo para o mockup de cada tela; Compose é implementação. Tabela de precedência no `CLAUDE.md` |
| D2 | O escopo é **adoção de primitiva por tela** | Contraste, código morto e presets não medidos ficam como débito registrado, não corrigido — ver *Fora de escopo* |
| D3 | Rastreio em **uma issue mestre + este doc** | Sem sub-issue por tela: mais lugares para o estado divergir |

---

## Levantamento — as superfícies visuais da aplicação

Feito em 2026-08-27 sobre `src/commonMain/.../presentation/ui/` e `src/desktopMain/`.
"Débitos" = pontos onde a tela desenha um retângulo que já tem primitiva publicada, contados por
`grep` de `Surface(`, `Card(`, `Modifier.border`, `.background(` com cor de superfície e
`RoundedCornerShape(`.

| # | Superfície | Arquivo de entrada | Janela | Débitos |
|---|---|---|---|---|
| 1 | Janela principal / Dashboard | `presentation/ui/DashboardScreen.kt` | própria | **0** — referência |
| 2 | Card de uso | `presentation/ui/components/ApiUsageCard.kt` | dentro da 1 | 6 |
| 3 | Faixa de atualização | `presentation/ui/DashboardScreenWarnings.kt` | dentro da 1 | 2 |
| 4 | Histórico | `presentation/ui/HistoryScreen.kt` | própria | 0 |
| 5 | Sessões CLI | `presentation/ui/CliSessionsScreen.kt` | própria | 5 |
| 5b | Sessões CLI — aba Resumo | `presentation/ui/CliUsageBreakdownPane.kt` | dentro da 5 | **0** — referência |
| 6 | Uso do time | `presentation/ui/TeamUsageScreen.kt` | própria | 5 |
| 7 | Presença do time | `presentation/ui/TeamPresenceScreen.kt` | própria | 2 |
| 8 | Chaves das contas | `presentation/ui/TeamKeysAdminScreen.kt` | própria (diálogo) | 2 |
| 9 | Configurações | `presentation/ui/components/SettingsDialogContent.kt` | própria (diálogo) | 6 |
| 10 | Notas da versão | `presentation/ui/ReleaseNotesContent.kt` | própria (diálogo) | 0 |
| 11 | Cromo das 8 janelas | `desktopMain/presentation/ui/DesktopWindowFrame.kt` | raiz de todas | 5 |
| — | Relatório PDF | `desktopMain/PdfUsageReportRenderer.kt` | — | fora: paleta própria, não é Compose |
| — | Bandeja | `desktopMain/TrayRiskIcon.kt` | — | fora: não é Compose |

**A fundação já bate.** `AppSpacing`, `AppShapes`, `AppElevation`, `AppMotion`, a escala
10/12/14/16/20/28, a divisão mono/sans e os hex de `OBSIDIANA_DARK` / `PORCELANA_LIGHT` /
`AppAccents` são idênticos aos `docs/design-system/tokens/*.css`. Não há literal de cor fora de
`theme/` (`grep -rn "Color(0x"` fora de `theme/` devolve zero). O que falta é adoção.

**Primitivas com adoção zero em produção**, medidas por chamada fora de `components/App*.kt`:

| Primitiva | Chamadas em produção | Onde deveria estar |
|---|---|---|
| `AppEmptyState` | 0 (só o próprio teste) | 9 estados vazios desenhados à mão |
| `AppTooltip` | 0 (nenhuma, nem em teste) | 4 bolhas com `Surface` próprio |
| `AppWindowScaffold` | 3 de 7 janelas | `TeamKeysAdminScreen`, `SettingsDialogContent`, `TeamPresenceScreen`, `DashboardScreen` |
| `AppToolbar` | 2 | telas que fixam parâmetros no topo |
| `DepthSurface` | 3 telas | duplica `AppDataSurface`; as três migram |

---

## Progresso por atividade

| Atividade | Superfície | Estado | Commit |
|---|---|---|---|
| A00 — Regra de precedência no `CLAUDE.md` + skill | — | ✅ | `6ed23fd` |
| A01 — Plano e issue de rastreio | — | ✅ | `df73cd2` |
| A02 — Uso do time | 6 | ✅ | `176bcb8` |
| A03 — Primitiva duplicada `DepthSurface` | 5, 7, 8 | ✅ | *(hash registrado na A04)* |
| A04 — Presença e chaves das contas | 7, 8 | ⬜ | |
| A05 — Sessões CLI | 5 | ⬜ | |
| A06 — Card de uso | 2 | ⬜ | |
| A07 — Faixa de atualização | 3 | ⬜ | |
| A08 — Configurações | 9 | ⬜ | |
| A09 — Tooltips | 4 arquivos | ⬜ | |
| A10 — `AppEmptyState` | 9 estados | ⬜ | |
| A11 — Cromo das janelas | 11 | ⬜ | |
| A12 — Protótipo, kit e capturas | — | ⬜ | |
| A13 — Fechamento | — | ⬜ | |

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

### A04 — Presença e chaves das contas

As duas telas ficaram na mesma atividade porque dependem da **mesma** extensão de primitiva.

- **`AppSectionHeader` precisa de recuo.** A anatomia dele — título, subtítulo e ação à direita — é
  exatamente a de `TeamPresenceAccountSubgroupHeader` (`:814`) e a de `TeamUsageScreen:997`, mas o
  padding horizontal dele é interno e fixo em `AppSpacing.md`, e as duas sub-faixas precisam de
  `PRESENCE_ROW_CONTENT_PADDING + recuo` (14dp + nível). Sem o parâmetro, adotar a primitiva
  desalinharia a faixa das linhas abaixo dela em 2dp. **Este é o item que fechou como pendente na
  A02** (`TeamAccountUuidHeader`).
- `Surface(background)` raiz de `TeamKeysAdminScreen` `:83` → `AppWindowScaffold`.
- `:683` `TeamPresenceEmailHeader` é irmã de `TeamAccountGroupHeader`: carrega colunas alinhadas ao
  `AppDataRow` do integrante e cai na mesma conclusão da O1 — conferir e registrar, não converter.
- Verificar: `--tests "com.usagemonitor.ui.TeamPresenceScreenTest"` (31),
  `--tests "com.usagemonitor.ui.TeamKeysAdminScreenTest"` (5),
  `--tests "com.usagemonitor.ui.TeamUsageScreenTest"` (52).

### A05 — Sessões CLI · `CliSessionsScreen.kt`

- `CostDistributionBar` `:1613` (`:1621` trilha, `:1634` segmentos) e a legenda `:1661` →
  `AppProgressTrack`. A barra é empilhada por tipo de token; se a primitiva não suportar segmentos, a
  extensão dela entra **neste mesmo commit**.
- `LiveBadge` `:572` → `AppStatusIndicator`.
- `GlossaryPanel` `:1564` / `.border` cru `:1545` → `AppDataSurface`.
- ⚠ `CACHE_READ_COLOR` (`:101`) é `darkAppAccents.cacheRead` congelado. O **uso** tocado aqui passa a
  `AppAccents.current.cacheRead`; os outros 32 usos ficam no débito (item 1 de *Fora de escopo*).
- Verificar: `--tests "com.usagemonitor.ui.CliSessionsScreenTest"` (45).

### A06 — Card de uso · `ApiUsageCard.kt`

- `Card(...)` do Material `:260` → `AppDataSurface`, com o arrasto por pressão longa preservado no
  `Modifier` externo. É o único `Card()` cru da aplicação.
- Os 4 pares `.background(surfaceVariant) + .border(...)` (`:800/801`, `:893/894`, `:1101/1102`,
  `:1267/1268`) → `AppMetricBlock` nos badges de cota, `AppDataSurface` nos blocos de resumo.
- `CardIconActionButton` `:1061` → `AppIconButton`.
- ⚠ **Armadilha nº 6**: `Modifier.border` arredonda o traço para cima e pinta depois do conteúdo — o
  anel de 1dp come uma caixa baixa a partir de densidade 1,05 (issue #83). A substituição é **fundo
  mais padding**, não `Modifier.border`. Prova de pintura é `captureToImage`, nunca `boundsInRoot`.
- Verificar: `--tests "com.usagemonitor.ui.ComponentTest"` (75) e `AppThemeScaleTest`.

### A07 — Faixa de atualização · `DashboardScreenWarnings.kt`

- `AppUpdateBanner` `:248` monta `Surface` `:268` + marcador `Box` `:292` → `AppBanner`.
- O contrato de `docs/design-system/components/shell/AppUpdateStrip.prompt.md` fixa 28dp e quatro
  estados (`available` / `downloading` / `ready` / `failed`), com `failed` sempre oferecendo o
  caminho manual — SmartScreen ou antivírus bloqueando o `Setup.exe` sem assinatura é desfecho
  esperado, não exceção. Conferir os quatro antes de trocar.
- Verificar: `--tests "com.usagemonitor.ui.AppUpdateBannerTest"` (8).

### A08 — Configurações · `SettingsDialogContent.kt`

- `SettingsSideNav` `:724`/`:733` e `SettingsNavItem` `:755`/`:776` → contrato de
  `AppSettingsNav.prompt.md`.
- `ThemePresetCard` `:945`/`:952` e as três amostras `:978`/`:981`/`:984` → `AppDataSurface` +
  `AppSourceMarker`.
- `HorizontalDivider` do Material → `AppDivider`.
- `Surface` raiz `:227` → `AppWindowScaffold`.
- `AppSliderThumb` `:1312` **fica**: o design system especifica trilha de 4dp e polegar de 12dp com
  os slots do `Slider` do Material, para a semântica de progresso continuar vindo dele.
- Verificar: `--tests "com.usagemonitor.ui.ComponentTest"`, `AutoUpdateToggleTest` (17),
  `ThemePresetPickerTest` (3).

### A09 — Tooltips

- `UsageTooltip.kt:50`, `TurnSeriesChart.kt:233`, `UsageHistoryLineChart.kt:510` e `:544` →
  `AppTooltip` (`AppControls.kt:459`), que já define a anatomia e tem zero chamadas.
- ⚠ A assinatura atual recebe texto e as bolhas carregam listas de métrica. Generalizá-la entra
  neste commit.
- Verificar: `--tests "com.usagemonitor.ui.ComponentTest"` + `commonTest`
  `UsageHistoryLineChartTest` (31).

### A10 — `AppEmptyState`

- Nove estados vazios desenhados à mão. A copy já está centralizada e já obedece à regra do design
  system de nomear o recorte ("nesta janela", "neste projeto") — **não reescrever texto**, só trocar
  o desenho: `CliSessionsFormatting.kt:180/199/205`, `CliUsageBreakdownLabels.kt:231`,
  `TeamUsageFormatting.kt:310/318/324`, `TeamKeysAdminScreen.kt:485`,
  `SettingsDialogContent.kt:687`, `TeamIntegrationSection.kt:165`, `DashboardScreen.kt:293`.
- Verificar: `gradlew.bat allTests`.

### A11 — Cromo das janelas · `DesktopWindowFrame.kt`

- `DesktopTitleBar` `:281` e `DesktopDialogTitleBar` `:360`: altura 34dp literal → constante ao lado
  de `TOOLBAR_HEIGHT` / `STATUS_BAR_HEIGHT` (`AppStructure.kt:74-75`), que já valem 34 e 30.
- 3 `HorizontalDivider` do Material → `AppDivider`.
- `TitleBarButton` `:429` → `AppIconButton`. O botão de fechar é a **única** exceção de hover do
  sistema (preenche `--crit` com branco) — preservar.
- ⚠ **Maior risco do plano.** O arquivo vive em `desktopMain` e não tem nenhum teste de componente.
  Esta atividade abre `src/desktopTest/.../ui/DesktopWindowFrameTest.kt` **antes** de tocar no
  código: três botões de cromo, o `compact` do modo somente cards e a faixa de hover.
- ⚠ `CompactTitleBarOverlay` `:165` usa arrasto imediato (`WindowDraggableArea`) e o card usa
  `detectDragGesturesAfterLongPress`. A faixa só pode ser composta durante o hover; presente o tempo
  todo, o arrasto da janela vence a pressão longa e reordenar o primeiro card fica impossível.
- Verificar: `gradlew.bat allTests` + `gradlew.bat run` em modo normal e em modo somente cards.

### A12 — Protótipo, kit e capturas

- Registrar no protótipo cada tela que mudou; atualizar os `.jsx` das 8 telas com kit.
- `gradlew.bat generateScreenshots` e comparar as 12 cenas com as anteriores.

### A13 — Fechamento

- Ponto de situação final aqui e na issue; fechar a issue.

---

## Registro de execução

Uma linha por commit, em ordem cronológica. Cada entrada carrega o comando que rodou e o resultado,
nunca a intenção.

| # | Commit | Atividade | O que foi feito | Verificação |
|---|---|---|---|---|
| 1 | `6ed23fd` | A00 | Tabela de precedência, regra de não reimplementar primitiva e regra de acento no `CLAUDE.md`; skill do design system registrada em `.claude/skills/usage-monitor-design/` | Diff inspecionado — mudança só de documentação, sem código |
| 2 | `df73cd2` | A01 | Este plano, com o levantamento das 13 superfícies e a medição de adoção por primitiva; issue de rastreio [#117](https://github.com/edilsonvilarinho/usage-monitor/issues/117) criada com o mesmo ponto de situação | `gh issue create` devolveu `issues/117`; contagens conferidas por `grep` sobre `presentation/ui/` |
| 3 | `176bcb8` | A02 | `Modifier.appNestedGroupItem` criada e adotada nos dois blocos aninhados; `TeamHealthCell` passou a ser `AppStatusIndicator` e perdeu o parâmetro `showLabel`, cujo ramo `true` era morto; o chamador da tela de presença acompanhou | `gradlew.bat desktopTest --tests TeamUsageScreenTest --tests TeamPresenceScreenTest` → 52 + 31 = **83 testes, 0 falhas** |
| 4 | *(a preencher na A04)* | A03 | `AppDataSurface` ganhou `verticalArrangement`; os quatro call sites de `DepthSurface` migraram com `Arrangement.Top`; `DepthSurface.kt` removido | `gradlew.bat desktopTest` sobre `TeamPresenceScreenTest`, `CliSessionsScreenTest`, `AppStructureTest`, `TeamKeysAdminScreenTest` e `TeamAdminSectionTest` → 31 + 45 + 5 + 5 + 6 = **92 testes, 0 falhas** |

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

**Consequência para o resto do plano:** cada atividade lê os call sites antes de aceitar a primitiva
que este documento nomeia, e **débito que atravessa telas vira atividade própria** em vez de item
repetido em cada uma. A contagem por `grep` mede o tamanho do débito, não a solução dele. As
atividades A03 a A11 seguem com a primitiva sugerida, agora explicitamente como hipótese.

| Data | Atividade | O que aconteceu | Como foi resolvido |
|---|---|---|---|
| 2026-08-27 | A02 | O plano nomeava `AppDataSurfaceFlush` e `AppSectionHeader` para call sites que não as comportam (O1 acima) | Primitiva nova (`appNestedGroupItem`) para um caso; os outros dois registrados como não convertíveis, com o motivo |
| 2026-08-27 | A02 | `TeamHealthCell` tinha um ramo `showLabel = true` que nenhum dos três chamadores usava | Ramo removido junto com o parâmetro, na migração para `AppStatusIndicator` |
| 2026-08-27 | A03 | `DepthSurface` atravessava três telas e não cabia dentro de nenhuma atividade de tela (O2) | Virou atividade própria, com a extensão de `AppDataSurface` e os quatro call sites no mesmo commit |
| 2026-08-27 | A03 | O plano mandava rodar `--tests "com.usagemonitor.ui.TeamAdminUiTest"`, e **nenhum teste roda com esse filtro** | `TeamAdminUiTest.kt` é um arquivo com duas classes, `TeamAdminSectionTest` (6) e `TeamKeysAdminScreenTest` (5). O filtro do Gradle casa nome de classe, não de arquivo, e não falha quando outro filtro da mesma chamada casa. Os nomes certos estão na seção *Verificação* |

---

## Fora de escopo — débito conhecido

Achados no levantamento de 2026-08-27, deixados de fora por D2. Registrados com a evidência para
poderem virar issue própria depois. **Não corrigir dentro desta iniciativa.**

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
   referenciado por `ComponentTest`. Decidir remover ou justificar.
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
