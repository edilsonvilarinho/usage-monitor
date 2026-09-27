# Dividir os arquivos e funções acima do limite (#302–#309) — execução

- Modelo: Claude Opus 5.5 (`claude-opus-5-5`), Claude Code CLI
- Data de início: 2026-09-26
- Branch: `refactor/split-oversized-files-302-309` (as oito issues numa branch só, a pedido do dono)
- Autor dos commits: identidade do repositório, com o trailer `Co-Authored-By: Claude Opus 5.5`

## Contexto

As issues #302 a #309 são o desdobramento da #298: o `ArchitectureRulesTest` congelou 15 arquivos
acima de 800 linhas e 5 funções acima de 300 numa lista de exceções com teto exato. Critério de
pronto comum às oito: cada item abaixo do limite e fora da lista, divisão por responsabilidade sem
arquivo-deus nem composable-deus novo, sem mudar comportamento, `gradlew.bat allTests` verde.

## Método

- **Mover, não reescrever.** A divisão é por declaração de topo, para um arquivo novo no **mesmo
  pacote**: nenhum import de consumidor muda e nenhum teste precisa ser tocado. Declaração
  `private` que passa a ser usada de outro arquivo vira `internal` — a mesma escolha da #298, que
  deixou os hosts no pacote raiz para não abrir visibilidade além do módulo.
- **Funções acima de 300 linhas** são quebradas em composables/funções menores com os mesmos
  parâmetros e a mesma ordem de efeitos; estado hasteado continua no mesmo dono.
- **Classes acima de 800 linhas** (`LocalCliSessionDataSource`, `DashboardViewModel`,
  `TeamUsageViewModel`, `AutoStartManager`) perdem o que não depende do estado da instância:
  SQL, funções puras, tipos auxiliares e, onde o estado é pequeno, colaboradores próprios.
- Cada atividade baixa ou remove os tetos do `ArchitectureRulesTest` no mesmo commit.

## Execução — uma atividade, um commit

| # | Issue | Atividade |
|---|---|---|
| A0 | — | Este plano |
| A1 | #309 | `AutoStartManager.kt` abaixo de 800 |
| A2 | #308 | `AppControls.kt` e `AppStructure.kt` abaixo de 800 |
| A3 | #306 | `UsageHistoryLineChart.kt` (arquivo e função) e `HistoryScreen.kt` |
| A4 | #307 | `HudNotch.kt` (arquivo e função) e `HudWindowHost` |
| A5 | #305 | `TeamUsageScreen.kt` (arquivo e `TeamUsageList`), `TeamPresenceScreen.kt`, `TeamUsageViewModel.kt` |
| A6 | #304 | `ApiUsageCard.kt` (arquivo e função) e `DashboardViewModel.kt` |
| A7 | #303 | `CliSessionsScreen.kt`, `CliUsageBreakdownPane.kt`, `LocalCliSessionDataSource.kt` |
| A8 | #302 | `SettingsDialogContent.kt` |
| A9 | todas | Listas de exceção vazias, `CLAUDE.md`/skill de design com os nomes novos, `allTests` |

## Pontos de situação

| # | Atividade | Commit | Evidência |
|---|---|---|---|
| A0 | Plano | `docs: plan split of oversized files (#302-#309)` | — |
| A1 | `AutoStartResult`, `AutoStartCommandResult` e os dois conversores saem para `AutoStartResult.kt`; os conversores passam de `private` a `internal`. `AutoStartManager.kt` 805 → 761 linhas, fora da lista | `refactor: split AutoStartManager below 800 lines (#309)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "*AutoStart*"`: 38 testes, 0 falhas |
| A2 | `AppControls.kt` 1.138 → 620 linhas: menu → `AppMenu.kt`, tooltip → `AppTooltip.kt`, chips de alternância/cor/glifo → `AppChips.kt`. `AppStructure.kt` 1.018 → 677: modificadores de profundidade → `AppSurfaceDepth.kt`, abas → `AppTabs.kt`, navegação lateral das Configurações → `AppSettingsNav.kt`. `CONTROL_HEIGHT` e `DISABLED_ALPHA` passam a `internal`. `CLAUDE.md` e as duas cópias da skill de design apontam os arquivos novos | `refactor: split AppControls and AppStructure below 800 lines (#308)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.ui.App*"`: 57 testes, 0 falhas |
| A3 | `UsageHistoryLineChart.kt` 1.293 → 756 linhas: tipos e funções puras de eixo, zoom, tooltip e formatação → `UsageHistoryChartModel.kt`. Função `UsageHistoryLineChart` 460 → 273: pintura do `Canvas` → `DrawScope.drawHistoryPlot`, gestos → `Modifier.historyChartPointerInput` (recorte e largura do plot entram como leitura, para dois eventos de rolagem entre recomposições continuarem encadeados como antes), rótulos dos eixos → `HistoryValueAxisLabels`/`HistoryTimeAxisLabels`. `HistoryScreen.kt` 1.095 → 556: cards de DeepSeek e OpenCode → `HistoryProviderCards.kt`, tabela de métricas → `HistoryMetrics.kt` | `refactor: split history screen and line chart below the limits (#306)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.ui.HistoryScreenTest" --tests "*UsageHistoryLineChart*"`: 47 testes, 0 falhas |
| A4 | `HudNotch.kt` 1.144 → 625 linhas: forma → `HudNotchShape.kt`, contagem e relógio → `HudCountdown.kt`, gesto de pressão → `HudPressGesture.kt`, faixa de anéis → `HudRingStrip.kt`. Função `HudNotch` 348 → 281: a medida do `Layout` virou `hudNotchMeasurePolicy`, recriada a cada composição como o lambda que substitui. `HudWindowHost` 335 → 294: balão da engrenagem → `HudWindowAppBalloon` (o escopo da exportação continua sendo do host, senão fechar o balão cancelaria a exportação), `hudUpdateIndicatorOf` e `handleHudWindowKey` | `refactor: split HudNotch and HudWindowHost below the limits (#307)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "*Hud*"`: 143 testes, 0 falhas |
| A5 | `TeamUsageScreen.kt` 1.589 → 737 linhas: faixas de conta → `TeamUsageAccountHeader.kt`, cabeçalho e legendas → `TeamUsageHeader.kt`, linha do integrante e célula de saúde → `TeamMemberRow.kt`, tendência e detalhe de sessão → `TeamUsagePanes.kt`. `TeamUsageList` 327 → 223: itens de integrante e sessões → `LazyListScope.teamMemberItems`, as duas linhas de erro de remoção → `TeamActionErrorRow`. `TeamPresenceScreen.kt` 1.013 → 653: cabeçalho e legendas → `TeamPresenceHeader.kt`, faixas de conta → `TeamPresenceAccountHeader.kt`. `TeamUsageViewModel.kt` 863 → 751: `LoadedTeam`, `flattenAccounts` (agora `flattenTeamAccounts`) e o detalhe agregado sem turnos (`aggregatedTeamSessionDetail`, que recebe o estado corrente em vez de lê-lo) → `TeamUsageViewModelSupport.kt` | `refactor: split team screens and view model below the limits (#305)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "*Team*"`: 327 testes, 0 falhas |
| A6 | `ApiUsageCard.kt` 1.676 → 416 linhas: atividade observada → `ApiUsageCardObservedUsage.kt`, cota minimizada → `ApiUsageCardCompactQuota.kt`, cota expandida → `ApiUsageCardQuotaRows.kt`, aviso de fonte → `ApiUsageCardNotice.kt`, cabeçalho, barra de navegação e botões → `ApiUsageCardHeader.kt`. Função `ApiUsageCard` 483 → 236 (`ApiUsageCardHeader`, `ApiUsageCardNavigationBar`, `ApiUsageCardQuotaContent`). `DashboardViewModel.kt` 1.223 → 790: fluxo de atualização automática (estado próprio: versão em voo, artefato preparado, backoff) → `DashboardUpdateCoordinator`, com a mesma API pública no view model e o mesmo escopo; casos de uso default das fontes não ligadas → `DashboardViewModelDefaults.kt`; regras sem estado (reset mais próximo, persistência do Codex, montagem do `UiState`, classificação da falha, retenção após falha, alvos habilitados, fusão da fila, riscos e anomalias do histórico) → `DashboardViewModelSupport.kt`. `FUNCTION_CEILINGS` ficou vazia. `CLAUDE.md` e `docs/architecture.md` apontam os nomes novos | `refactor: split ApiUsageCard and DashboardViewModel below the limits (#304)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "*Dashboard*" --tests "*ApiUsageCard*" --tests "com.usagemonitor.ui.ComponentTest"`: 204 testes, 0 falhas; depois do `emptyMap()`, `--tests "com.usagemonitor.architecture.*"` verde |
| A7 | `CliSessionsScreen.kt` 1.703 → 493 linhas: painel de detalhe → `CliSessionDetail.kt`, métricas, saúde, glossário e distribuição de custo → `CliSessionDetailParts.kt`, linha, legendas e colunas → `CliSessionRow.kt`. `CliUsageBreakdownPane.kt` 899 → 600: orçamento, tabelas de balde e de ferramenta e grade → `CliUsageBreakdownTables.kt`. `LocalCliSessionDataSource.kt` 1.696 → 769: as 50 constantes e SQL do companion → `LocalCliSessionIndexSql.kt` (top-level `internal`); schema, migração, backfill e registro de arquivos → `LocalCliSessionIndexSchema.kt` (`initializeCliIndexSchema` recebe o hostname como leitura, para ele continuar resolvido só quando a coluna nasce); descoberta, parse incremental e cauda dos transcripts → `LocalCliSessionTranscripts.kt` (recebem o `Json` da instância). Nomes de SQL e de constante inalterados | `refactor: split CLI sessions screen and index below 800 lines (#303)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "*CliSession*" --tests "*CliUsage*" --tests "*Breakdown*" --tests "*Stalled*" --tests "*SessionPulse*"`: 333 testes, 0 falhas |
| A8 | `SettingsDialogContent.kt` 1.853 → 446 linhas, com `SettingsTab`, o diálogo e `SettingsOptionRow` (agora `internal`): aba Geral → `SettingsGeneralTab.kt`, aba APIs e diálogo de chave → `SettingsApisTab.kt`, aba Contas com os seletores de cor e emoji → `SettingsAccountsTab.kt`, tema e idioma → `SettingsThemeControls.kt`, interruptores e textos da atualização automática → `SettingsToggles.kt`, controles deslizantes → `SettingsSliders.kt`. `FILE_CEILINGS` ficou vazia | `refactor: split SettingsDialogContent below 800 lines (#302)` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.ui.SettingsDialogContentTest"`: 23 testes, 0 falhas |
| A9 | Imports sem uso podados nos 58 arquivos de produção tocados; `CLAUDE.md` e `AGENTS.md` registram as listas de exceção vazias e os arquivos que ficaram perto do limite | `refactor: prune imports and close oversized files plan (#302-#309)` | `gradlew.bat allTests`: 2.217 testes, 0 falhas (219 classes), mesma contagem do fechamento da #298. Maiores arquivos de produção depois: `ApiUsageCardFormatting.kt` 796 (não era exceção), `DashboardViewModel.kt` 790, `LocalCliSessionDataSource.kt` 769, `AutoStartManager.kt` 761, `UsageHistoryLineChart.kt` 756, `TeamUsageViewModel.kt` 751 |

## Problemas em aberto e riscos

| Risco | Estado | Nota |
|---|---|---|
| "Tela conferida no app" (critério das oito issues) não é verificável por teste | aberto | Nenhuma mudança visível pretendida e nenhum teste de componente mudou de resultado. A conferência no app **não foi feita** nesta execução: o `gradlew run` com o app instalado aberto vira segunda instância e sai. Roteiro: Configurações (seis abas), dashboard com card aberto e minimizado, Histórico com zoom por roda, Sessões CLI (lista, detalhe, resumo), modais do time, HUD (hover, balão da engrenagem, arrasto pela mão) |
| Arquivos entre 750 e 800 linhas depois da divisão | aceito | Abaixo do limite, que é o critério; a próxima mudança neles começa extraindo. Registrado no `CLAUDE.md` |
| `HudWindowHost` com 294 linhas e `SettingsDialogContent` com ~294, perto do limite de função | aceito | Idem; `SettingsDialogContent` já estava abaixo de 300 e não era exceção |
| `private` → `internal` amplia a visibilidade dentro do módulo | aceito | Mesmo pacote, mesmo módulo; alternativa seria subpacote, que abriria mais |
| Branch única com oito issues gera PR grande | aceito | Pedido explícito; commits por issue permitem revisão e revert por issue |

## Desvios do plano e achados da execução

- **Nenhum teste precisou mudar**, exceto o próprio `ArchitectureRulesTest`: as duas listas viraram
  `emptyMap()` tipado (um `mapOf()` vazio não infere tipo e o teste não compila).
- **Três extrações foram além de mover declaração de topo**, porque a classe inteira era a
  exceção: `DashboardUpdateCoordinator` (estado próprio da atualização automática, mesma API
  pública no view model), as funções de schema/transcript do índice CLI (recebem `Json` e o
  hostname por parâmetro) e as regras sem estado de `DashboardViewModel` e `TeamUsageViewModel`.
- **Leitura em vez de valor onde o tempo importa**: o recorte do zoom e a largura do plot no
  gesto do gráfico, o hostname do backfill do índice e o estado do dashboard na exportação pelo
  balão da HUD entram como lambda, para continuarem lidos no momento do evento como antes.
- **O escopo da exportação no balão da HUD continua sendo do host**: o balão sai da composição
  quando o ponteiro deixa a HUD, e um escopo dele cancelaria a exportação.
- **A primeira passada da extração do índice CLI abortou no meio** (asserção de indentação depois
  de o arquivo já ter sido escrito). O arquivo foi restaurado do `HEAD` e as duas etapas refeitas;
  nada da passada abortada chegou a commit.
- Os nomes `flattenAccounts` → `flattenTeamAccounts`, `aggregatedDetail` →
  `aggregatedTeamSessionDetail` e `initializeSchema` → `initializeCliIndexSchema` mudaram por
  virarem declarações de topo no pacote; as referências no `CLAUDE.md` foram atualizadas.
