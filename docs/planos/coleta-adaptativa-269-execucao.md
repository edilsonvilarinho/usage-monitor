# Coleta adaptativa por alvo (#269) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-09-27 |
| **Issue** | [#269](https://github.com/edilsonvilarinho/usage-monitor/issues/269) |
| **Branch** | `feat/269-adaptive-polling`, criada de `main` (`adf1884`) |
| **PR alvo** | `main` |

## Objetivo

Trocar o poll global fixo de 10 min (`DashboardViewModelConfig.pollInterval = 600.seconds`) por coleta
adaptativa **por alvo**, no modelo do Codenotch:

- 60 s com sessão CLI ativa, 5 min sem nenhuma;
- coleta extra quando um reset vence ou o PC volta do sleep;
- backoff persistido no 429 (`min(15min, max(60s·2^min(n,4), Retry-After))`);
- a última leitura boa continua na tela quando a coleta falha (até 7 dias).

O desenho completo, as duas referências (Codenotch e ai-usagebar) e a justificativa de cada decisão estão na
descrição da issue. Este documento registra o que diverge dela e o que foi executado.

**Acréscimo pedido pelo usuário:** com cadência de 60 s e prazos por alvo, a contagem `05:42` + relógio na faixa do
notch da HUD (#185/#293) vira um número que reinicia a cada minuto. Ela **sai da faixa** e continua só no balão da
engrenagem, que já a mostrava. O rodapé do modo padrão não muda.

## Divergências entre a issue e o código atual

Verificadas em `adf1884`:

| Ponto da issue | Estado real |
|---|---|
| Linhas citadas de `DashboardViewModel.kt` (L419-449, L619-650, L685-713, L774-837) | Defasadas pelo refactor #298/#302–#309. Reais: `startCountdown` L309, `performFetch` L424, `refresh(*)` L530–573, `handleTargetFailure` L625, `scheduleNextRefresh` L784 |
| Fiação em `Main.kt` | O VM é montado em `AppViewModels.kt`; `persistedNextRefreshAt` é lido em `AppGraph.kt` |
| `canPreserveCodexCache` em `performFetch` | Mora em `statsRetainedAfterFailure`, `DashboardViewModelSupport.kt` |
| Espaço no VM | `DashboardViewModel.kt` tem **790** linhas e o teto do `ArchitectureRulesTest` é 800: o agendamento vai para arquivo próprio e o VM só delega |

## Atividades

| # | Atividade |
|---|---|
| C0 | Este plano |
| C1 | Fase 1 — 429 tipado com `Retry-After`, backoff por alvo persistido, 429 do endpoint de token, última leitura para toda fonte com `fetchedAt` e teto de 7 dias |
| C2 | Fase 2 — agendamento por alvo: 60 s ativo / 5 min ocioso, reset só dos alvos vencidos, volta do sleep, contas Anthropic espaçadas, `refresh(target)` isolado |
| C3 | Contagem fora da faixa do notch, só no balão da engrenagem |
| C4 | Fase 3 — publicação incremental, timeouts separados da Anthropic, Antigravity interrompível |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| C0 | Plano no repositório | `git checkout -b feat/269-adaptive-polling` | branch criada de `adf1884`; só documentação |
| C1 | Fase 1: `RateLimitedException` + `parseRetryAfter` (GET de uso e renovação do token), `DashboardRefreshScheduler` com backoff persistido (`RefreshSchedulePreferences.kt`), retenção generalizada com teto de 7 dias, banner/toast com a hora; fila de coletas extraída para `DashboardFetchQueue` (VM 790 → 759 linhas) | `gradlew.bat allTests` | `BUILD SUCCESSFUL in 3m 44s`, zero falhas. Testes novos: `RefreshScheduleTest`, `RateLimitHttpTest`, `DashboardViewModelRateLimitTest` (7), token 429 em `LocalCredentialDataSourceTest`, renovação que falha não chama o uso em `AnthropicRepositoryImplTest`, banner em `DashboardScreenWarningsTest`. Ajustados: round-trip do cache (herda `savedAt`) e toast de 429 (traz `retryAt`). O User-Agent `claude-code/1.0.0` ficou: nenhuma versão mais nova foi validada contra o endpoint |
| C2 | Fase 2: `isTargetDue`/`nextDueAt`/`hasQuotaResetSince`/`looksLikeWakeFromSleep` puros; `startCountdown` reescrito por alvo; `activePollInterval` 60 s / `idlePollInterval` 5 min / `anthropicStagger` 800 ms; `isBusy` de `SessionPulseViewModel.activeTargets` via ponte em `AppViewModels`; `currentPollInterval` substitui `pollInterval`; funil de falhas extraído para `DashboardFailureHandler` (VM 796 → 754 linhas) | `gradlew.bat allTests` | `BUILD SUCCESSFUL in 3m 45s`, zero falhas. Novos: `DashboardViewModelCadenceTest` (5 min ocioso, 60 s ativo, sessão começando antecipa, contas Anthropic ≥ 800 ms), quatro casos puros em `RefreshScheduleTest`. Substituído: o teste que afirmava que `refresh(source)` reiniciava a contagem global agora afirma o contrário. A volta do sleep só tem teste da regra pura: o relógio virtual não salta |
| C2b | Race no teste de persistência: a contagem passou a ser publicada depois da tela, e `refresh persists the new scheduled time via callback` lia `null` às vezes | `gradlew.bat desktopTest --tests "…DashboardViewModelRefreshPersistenceTest" --rerun-tasks` (3×) | falhou 1× no `allTests` do C3 (`expected:<…> but was:<null>`); com a espera pelo callback, 3 de 3 verdes. Commit próprio, `3c1b476` |
| C3 | Contagem fora da faixa do notch: `HudNotch`/`HudRingStrip` sem o slot, `hudNotchSizes` sem `showsCountdown`, `countdownWidth` apagado; o `HudCountdown` fica só no balão da engrenagem, com a volta em `currentPollInterval`. Ajuda PT/EN, `CLAUDE.md`, protótipo e design system (`AppHudBar`) atualizados; `img/hud*.png`, `img/hud.gif` e `help/window-modes.gif` regenerados | `gradlew.bat generateScreenshots generateHelpMedia`; `gradlew.bat allTests` | captura `img/hud-rest.png` conferida: faixa só com os anéis. `BUILD SUCCESSFUL in 3m 42s`, zero falhas. `dashboard.gif`, `history.gif` e `updates.gif` também mudaram na regeneração sem desenhar o notch — revertidos, fora do escopo |
