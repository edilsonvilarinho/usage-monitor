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
| A5 | #324: `CodexRolloutRateLimitParser` (último `rate_limits` por `limit_id`), `LocalCodexRolloutRateLimitDataSource` (5 rollouts mais novos, 256 KB finais), `CodexMapper.modelLimitQuotas` (sem `codex`, sem plano divergente, sem janela vencida, percentual truncado), `CodexRepositoryImpl` soma depois das janelas ao vivo e ignora falha do rollout; `CodexQuotaLabels.groupOf` no título do card e da HUD. `docs/integrations.md` §Codex corrigido (a exigência das duas janelas não existia mais no código) | `gradlew.bat desktopTest --tests` `CodexRolloutRateLimitParserTest`, `CodexMapperTest`, `CodexRepositoryImplTest`, `LocalCodexRolloutRateLimitDataSourceTest`, `ApiUsageCardFormattingTest`, `HudModelTest`; teste descartável lendo o `~/.codex` real | 2/2, 11/11, 7/7, 4/4, 13/13, 34/34 verdes. Leitura real: 215 ms, um limite (`codex`, `plus`), nenhuma cota extra — a conta não tem limite por modelo. O caminho do limite por modelo só está coberto por fixture |
| A6 | #329, escopo escolhido pelo usuário ("Cards por conta"): `CodexProfileRef`; `UsageTargetKey` aceita perfil no Codex (a conta padrão segue sem, chave `CODEX` intacta); `CodexRepository.getUsage(profile)` + `CodexProfileSources`; `CodexProfileRegistry` (nó `codexProfiles`, id `codex-…`, recusa diretório sem `auth.json` e o da conta padrão); `DashboardTargetFetcher` extraído do view model (754 → 743 linhas); `enabledTargetsOf`/`availableUsageTargets` com as extras depois da padrão; `uiApiErrorOf` nomeia "Codex — <conta>"; `anthropicProfileId` nas três consultas de cor/emoji; seção "Contas Codex extras" em Configurações › Contas. Protótipo (§12d) e kit `Settings.jsx` atualizados | `gradlew.bat desktopTest --tests` `DashboardViewModelCodexProfilesTest`, `CodexRepositoryImplTest`, `UsageTargetKeyTest`, `CodexProfileRegistryTest`, `ui.SettingsDialogContentTest` | 3/3, 9/9, 5/5, 4/4, 19/19 verdes. O primeiro `allTests` reprovou em `ArchitectureRulesTest` (`SettingsDialogContent`: 313 linhas, limite 300): os cinco parâmetros do Codex viraram `CodexAccountsSettings` e a seção passou a ser composta dentro de `AnthropicAccountsTab`. Depois, `gradlew.bat allTests`: 2345 testes, 0 falhas. Na mesma rodada reprovada caiu também `DashboardViewModelRefreshPersistenceTest > refresh persists the new scheduled time via callback` (`Condition not met within real-time timeout`); isolado com `--rerun-tasks`, 3 de 3 verdes — é a instabilidade sob carga já registrada no plano da #317, anterior a esta branch. Não houve teste com duas contas Codex reais: a leitura por diretório está coberta por fake do `CodexAuthDataSource` |
| A7 | Fechamento: suíte completa na ponta da branch, desvios e achados registrados | `gradlew.bat allTests` (depois do commit da A6) | 2345 testes, 0 falhas, 0 ignorados. Nenhuma verificação manual no app empacotado nesta rodada: bandeja, Configurações › Contas e marca de ritmo foram provados só por teste de componente e bitmap |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| #325: não há máquina com antivírus que inspecione HTTPS para validar de ponta a ponta. O teste cobre só a composição dos `TrustManager` | aberto |
| #325: `KeychainStore` no macOS: suporte macOS nunca validado em Mac real | aberto |
| #324: não há conta com limite por modelo ativo. A forma do evento é a observada localmente (`limit_id` `codex`/`premium`) mais o relato do concorrente | aberto — só fixture |
| #324: cada coleta do Codex (60 s com sessão ativa) lê até 5 × 256 KB de rollout em `Dispatchers.IO` | aceito |
| #328: legibilidade do anel em 16 px nas bandejas do Linux (AppIndicator) e do macOS não verificada em máquina real | aberto |
| #329: a renovação do token Codex é feita pelo próprio CLI no `auth.json` do diretório. Perfil apontando para diretório que o CLI não usa fica com token vencido | aceito — documentado na seção e em `integrations.md` |
| #329: nenhuma verificação com duas contas Codex reais; o `cap_sid` de conta extra depende do Codex Desktop gravá-lo no mesmo diretório | aberto |

## Desvios do plano e achados da execução

- **Premissa errada na #324.** A issue dizia que o Codex descartava leitura com `secondary_window: null`.
  O código já aceitava uma janela só; o que exigia as duas era o `docs/integrations.md`, desatualizado.
  Corrigido em comentário na issue antes da execução, e o escopo virou "limite por modelo pelo rollout".
- **A primeira versão do teste da #326 não provava nada**: a injeção pelo `onNextRefreshAtChanged`
  passava sem a correção, porque o callback só roda quando o prazo muda. Trocada por um relógio que
  lança dentro da volta; o teste novo reprova sem o `catch`.
- **Limite de 800 linhas cobrou duas extrações**: `QuotaTooltipMetrics.kt` saiu do
  `ApiUsageCardFormatting.kt` (796 → 779) na A3, e `DashboardTargetFetcher.kt` do
  `DashboardViewModel.kt` (754 → 743) na A6. O limite de 300 linhas por função cobrou o
  `CodexAccountsSettings` na A6.
- **Escopo da #329 decidido pelo usuário no meio da execução** ("Cards por conta"): cor, emoji, filtro
  de sessões CLI e envio ao time por conta ficaram de fora. Motivo medido: 216 usos de `profileId`
  que significavam "perfil Anthropic".
- **`buildHttpClient` virou `internal`** (A2): o parâmetro novo é de tipo interno, e o compilador
  recusa expor tipo interno em função pública. Nenhum chamador fora do módulo.
- **Instabilidade conhecida reapareceu** na primeira rodada completa da A6
  (`DashboardViewModelRefreshPersistenceTest`), verde isolada 3 de 3. Não corrigida aqui.
