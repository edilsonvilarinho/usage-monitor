# Conformidade com o design system — plano de execução

> Fonte de verdade desta iniciativa. Rastreio no GitHub: issue [**#117**](https://github.com/edilsonvilarinho/usage-monitor/issues/117).
>
> Quem retomar a iniciativa lê o **Ponto de situação** primeiro e não precisa de mais nada.
> Cada atividade é um commit atômico, e a linha do *Registro de execução* entra **no mesmo commit**
> da atividade que ela descreve — em commit separado a linha pode existir sem a mudança, e o
> registro deixa de servir para auditoria.

---

## Ponto de situação

**Estado atual:** `Em andamento — A00 e A01 concluídas`
**Última atualização:** 2026-08-27
**Branch:** `main`

### ▶ Atividade corrente

**A02 — Cabeçalhos de grupo do Uso do time.** `TeamUsageScreen.kt` pinta cinco superfícies à mão
(`:650`, `:687`, `:840`, `:997`, `:1041`) onde já existem `AppSectionHeader`,
`AppDataSurfaceFlush` e `AppStatusIndicator`.

### ⏭ Próxima atividade

**A03 — Cabeçalhos de grupo da Presença do time.** `TeamPresenceScreen.kt` `:683` e `:814`, mais os
dois usos de `DepthSurface`.

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
| A01 — Plano e issue de rastreio | — | ✅ | *(hash registrado na A02)* |
| A02 — Uso do time | 6 | ⬜ | |
| A03 — Presença do time | 7 | ⬜ | |
| A04 — Chaves das contas | 8 | ⬜ | |
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

### A02 — Uso do time · `TeamUsageScreen.kt`

- `:840` `TeamAccountGroupHeader` e `:997` `TeamAccountUuidHeader` → `AppSectionHeader` com
  `markerColor` (é o marcador de 2dp que os dois pintam à mão).
- `:650` e `:687`, blocos de sessão aninhados → `AppDataSurfaceFlush`.
- `:1041` `TeamHealthCell` → `AppStatusIndicator`, que já é ponto + palavra.
- **A escada de três superfícies tem de sobreviver**: faixa da conta em `surfaceVariant`, linha do
  integrante transparente, bloco aninhado em `surface`. O bloco aninhado **nunca** em
  `surfaceVariant` — aquele é o realce de hover do `AppDataRow`, e com ele ali passar o mouse numa
  sessão deixa de dar retorno.
- Verificar: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamUsageScreenTest"` (52 testes).

### A03 — Presença do time · `TeamPresenceScreen.kt`

- `:683` `TeamPresenceEmailHeader` e `:814` `TeamPresenceAccountSubgroupHeader` → `AppSectionHeader`.
- `DepthSurface` (2 usos) → `AppDataSurface`.
- Verificar: `--tests "com.usagemonitor.ui.TeamPresenceScreenTest"` (31).

### A04 — Chaves das contas · `TeamKeysAdminScreen.kt`

- `DepthSurface` em `TeamKeyCard` `:291` → `AppDataSurfaceFlush` + `AppSectionHeader`.
- `Surface(background)` raiz `:83` → `AppWindowScaffold`.
- Verificar: `--tests "com.usagemonitor.ui.TeamAdminUiTest"` (11).

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
| 2 | *(a preencher na A02)* | A01 | Este plano, com o levantamento das 13 superfícies e a medição de adoção por primitiva; issue de rastreio [#117](https://github.com/edilsonvilarinho/usage-monitor/issues/117) criada com o mesmo ponto de situação | `gh issue create` devolveu `issues/117`; contagens conferidas por `grep` sobre `presentation/ui/` |

---

## Ocorrências adversas

| Data | Atividade | O que aconteceu | Como foi resolvido |
|---|---|---|---|
| — | — | Nenhuma até aqui | — |

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
7. **Nomes divergentes entre o design system e o Kotlin.** `AppWindowFrame` × `AppWindowScaffold`,
   `AppUpdateStrip` × `AppUpdateBanner`, `AppColumnHeader` × `AppColumnHeaderRow`, `AppPanel` ×
   `AppDataSurface`, `AppMetric` × `AppMetricBlock`, `AppSourceMark` × `AppSourceMarker`. Renomear de
   um lado ou do outro é decisão pendente; enquanto ela não vier, o mapeamento vive aqui.

---

## Verificação

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
