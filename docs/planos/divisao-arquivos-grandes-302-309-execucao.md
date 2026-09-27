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

## Problemas em aberto e riscos

| Risco | Estado | Nota |
|---|---|---|
| "Tela conferida no app" (critério das oito issues) não é verificável por teste | aberto | Refatoração sem mudança visível; a conferência manual fica registrada como pendente se não for feita |
| `private` → `internal` amplia a visibilidade dentro do módulo | aceito | Mesmo pacote, mesmo módulo; alternativa seria subpacote, que abriria mais |
| Branch única com oito issues gera PR grande | aceito | Pedido explícito; commits por issue permitem revisão e revert por issue |

## Desvios do plano e achados da execução

(preenchido ao longo da execução)
