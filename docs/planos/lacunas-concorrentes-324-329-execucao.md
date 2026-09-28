# Lacunas frente aos concorrentes (#324–#329) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (desktop) |
| **Data** | 2026-09-27 |
| **Issues** | [#324](https://github.com/edilsonvilarinho/usage-monitor/issues/324), [#325](https://github.com/edilsonvilarinho/usage-monitor/issues/325), [#326](https://github.com/edilsonvilarinho/usage-monitor/issues/326), [#327](https://github.com/edilsonvilarinho/usage-monitor/issues/327), [#328](https://github.com/edilsonvilarinho/usage-monitor/issues/328), [#329](https://github.com/edilsonvilarinho/usage-monitor/issues/329) |
| **Branch** | `feat/competitive-gaps-324-329`, criada de `main` (`616d123f`). Uma branch para as seis, a pedido do usuário, para evitar vários merges |
| **PR alvo** | `main` |

## Contexto

Análise das issues de `vinzdg/codenotch` (24 abertas) e `akitaonrails/ai-usagebar` (2 abertas, ~45
fechadas recentes) contra o nosso código. As seis issues são as lacunas P0/P1 que ficaram de pé
depois de conferir o código. O que já medimos antes de começar:

- **#326**: o laço `startCountdown` (`DashboardViewModel.kt`) é um `while (true)` sem `try/catch`.
  `dueTargets`, `publishNextPoll` (que chama `onNextRefreshAtChanged` → `Settings.putLong`) e
  `nextQuotaResetTarget` rodam dentro dele. Uma exceção qualquer ali encerra o `countdownJob`; o
  `SupervisorJob` mantém o resto do app vivo, e a coleta automática para sem aviso. É a mesma forma
  do defeito do codenotch#316.
- **#325**: o runtime do app instalado (`%LOCALAPPDATA%\Usage Monitor\runtime\release`) declara
  `MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.logging
  java.transaction.xa java.sql jdk.crypto.ec"`. Sem `jdk.crypto.mscapi` o repositório
  `Windows-ROOT` nem existe no app empacotado. O `HttpClient(OkHttp)` (`HttpClientFactory.kt`) usa
  o `TrustManager` padrão da JVM, ou seja, o `cacerts` embarcado.
- **#327**: `QuotaInfo` não tem início de janela. As fontes que informam a duração: Anthropic pelo
  nome da chave (`five_hour`, `seven_day`), Codex por `limit_window_seconds`, MiniMax por
  `start_time`/`weekly_start_time`.
- **#328**: `TrayRiskIconPainter` pinta só o ponto do pior risco.
- **#324**: a premissa da issue estava errada, e está corrigida em comentário nela. O
  `CodexMapper` aceita uma janela só. O que falta é o limite por modelo: os rollouts locais trazem
  `rate_limits` com `limit_id`/`limit_name` em cada `token_count`.
- **#329**: multi-conta existe só para Anthropic. `UsageTargetKey` recusa perfil fora da Anthropic.

## Atividades

- **A0**: este plano.
- **A1 (#326)**: isolar cada volta do laço de coleta. Exceção que não for `CancellationException`
  vira breadcrumb, e a volta seguinte espera um intervalo curto antes de recalcular. Teste com
  `onNextRefreshAtChanged` que lança: a coleta seguinte precisa sair.
- **A2 (#325)**: `TrustManager` composto (JVM + repositório do SO: `Windows-ROOT` no Windows,
  `KeychainStore` no macOS), com degradação para o padrão se o repositório do SO não carregar.
  `jdk.crypto.mscapi` entra em `modules(...)` só no build Windows, porque jlink em Linux/macOS
  falharia com módulo inexistente.
- **A3 (#327)**: `QuotaInfo.periodStartAt: Instant? = null` (campo novo com default, retrocompatível).
  Preenchido por Anthropic, Codex e MiniMax e carregado pelo cache do dashboard. Na barra de cota,
  marca do tempo decorrido, só com reset conhecido e início informado. Design system e protótipo
  no mesmo commit.
- **A4 (#328)**: preferência "anel de uso no ícone da bandeja", desligada por padrão. O anel
  mostra o maior percentual entre as cotas vigentes, e o ponto de risco continua.
- **A5 (#324)**: limites por modelo lidos do rollout mais recente e exibidos como cotas extras no
  card do Codex, sem substituir a leitura ao vivo. Leitura parada (reset vencido) é descartada.
  `docs/integrations.md` §Codex corrigido.
- **A6 (#329)**: perfis Codex por diretório (`CODEX_HOME` alternativo), no modelo do registro
  Anthropic.
- **A7**: fechamento: `allTests`, desvios e achados.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A0 | Plano no repositório | `git switch -c feat/competitive-gaps-324-329` | branch criada de `616d123f`; só documentação |
| A1 | #326: corpo do laço extraído para `runCountdownTick` e isolado por `try/catch` (rethrow de `CancellationException`, breadcrumb `ERROR`, espera `pollLoopRecoveryDelay` = 30 s). Teste novo com relógio que lança uma vez na volta dos 5 min | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.DashboardViewModelCadenceTest"` com e sem o `catch` | Com o `catch`: verde. Sem ele: `a failing tick does not stop the collection loop FAILED`. A 1ª versão do teste (injeção por `onNextRefreshAtChanged`) passava **sem** a correção, porque o callback só roda quando o prazo muda e não lançava dentro do laço; foi descartada |
| A2 | #325: `SystemTrustStore.kt` (`CompositeX509TrustManager`, `buildSystemTlsTrust`) ligado no `buildHttpClient` via `sslSocketFactory`; `buildHttpClient` virou `internal` (o parâmetro novo é tipo interno). `jdk.crypto.mscapi` no `modules(...)` só no Windows | `gradlew.bat desktopTest --tests "com.usagemonitor.SystemTrustStoreTest" --tests "com.usagemonitor.HttpClientFactoryTest"`; `gradlew.bat createRuntimeImage` + `grep MODULES build/compose/tmp/main/runtime/release` | Testes verdes (6 novos, incluindo `Windows-ROOT` carregando no JDK de desenvolvimento com emissores não vazios). Runtime novo: `MODULES="… jdk.crypto.ec jdk.crypto.mscapi"`; o do app instalado (v41.1.0) não tinha o módulo. A imagem de runtime sai sem `java.exe`, então não houve execução dentro dela |
| A3 | #327: `QuotaInfo.periodStartAt` + `elapsedFractionAt`; Anthropic/Codex/MiniMax preenchem; cache carrega; `AppProgressTrack(marker)` pinta o traço; tooltip e `stateDescription` dizem o valor. `addProjectionMetric` saiu para `QuotaTooltipMetrics.kt` (o `ApiUsageCardFormatting.kt` estava em 796 linhas e foi a 779). Design system (`AppProgressTrack.prompt.md`/`.jsx`/`.d.ts`, kit `Dashboard.jsx`) e protótipo (`.track > b`) atualizados | `gradlew.bat desktopTest --tests` `AppStatesTest`, `QuotaInfoTest`, `AnthropicMapperTest`, `CodexMapperTest`, `MiniMaxMapperTest`, `DashboardCacheMapperTest`; depois `ComponentTest` + `architecture.*` | Todos verdes: `AppStatesTest` 13/13, `QuotaInfoTest` 10/10, `ComponentTest` 78/78. O teste de bitmap afirma que a marca muda pixels só nas colunas 178–182 de um trilho de 300px (fração 0,6) e nas 4 linhas do trilho |
| A4 | #328: preferência `trayUsageRing` (default `false`) de `PreferencesSettings` até o `TrayUsageRingToggle` na seção Sistema; `trayUsageRingFraction` (maior percentual entre cotas vigentes, sem `CURRENCY_USD` nem janela vencida); `TrayRiskIconPainter` desenha trilho escuro + arco. Protótipo (figura e nota na seção da marca) e `brand-tray.html` atualizados | `gradlew.bat desktopTest --tests` `presentation.TrayUsageRingTest`, `TrayRiskIconTest`, `TrayUsageRingPreferencesTest`, `ui.SettingsDialogContentTest` | 3/3, 4/4, 2/2, 18/18 verdes. `TrayRiskIconTest` mede pixel num bitmap 64×64: com 50% a borda direita tem a cor do arco e a esquerda não |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| #325: não há máquina com antivírus que inspecione HTTPS para validar de ponta a ponta. O teste cobre só a composição dos `TrustManager` | aberto |
| #325: `KeychainStore` no macOS: suporte macOS nunca validado em Mac real | aberto |
| #324: não há conta com limite por modelo ativo. A forma do evento é a observada localmente (`limit_id` `codex`/`premium`) mais o relato do concorrente | aberto |
| #328: legibilidade do anel em 16 px nas bandejas do Linux (AppIndicator) e do macOS não verificada em máquina real | aberto |
| #329: a renovação do token Codex é feita pelo próprio CLI no `auth.json` do diretório. Perfil apontando para diretório que o CLI não usa fica com token vencido | aberto |

## Desvios do plano e achados da execução

(preenchido na A7)
