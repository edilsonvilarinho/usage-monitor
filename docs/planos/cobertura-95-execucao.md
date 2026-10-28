# Cobertura de linhas a 95% com catraca

## Objetivo

O dono do repositório delegou o ciclo inteiro (código, PR, CI, merge) ao agente; a suíte é a única rede. Regra nova
(2026-10-27): **95% de cobertura de linhas**, no Kotlin e no servidor de time, imposta por catraca no CI. Substitui o
"sem piso" da #299 (`docs/build-and-release.md`, "CI e testes — decisões").

## Fatos confirmados (medidos em 2026-10-27, `af450570`, Windows, skill `usage-monitor-test-report`)

- `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport`: 3 296 testes, 0 falhas.
  Kover: **38 607 de 46 302 linhas (83,4%)**, 7 695 descobertas; ramos 58,4%.
- `npm run test:coverage` (`server/`): 299 testes, 0 falhas; v8: **920 de 992 linhas (92,7%)**, 72 descobertas,
  30 delas em `src/main.ts` (arranque).
- Para 95% no Kotlin, com o denominador de hoje, faltam **5 379 linhas cobertas** (70% de tudo o que está
  descoberto). No servidor faltam 23.
- Arquivos que importam `java.awt`, `javax.swing`, `com.sun.jna`, `androidx.compose.ui.window` ou
  `androidx.compose.ui.awt` somam **1 661** das 7 695 linhas descobertas (`HudWindow.kt` 260, `AppDialogWindow.kt`
  205, `SettingsWindowHost.kt` 129, `DesktopWindowFrame.kt` 94, `CompositorWindowDrag.kt` 94…). Nenhum teste
  exercita o sistema de janelas (CLAUDE.md, "CI e testes"; `CONTRIBUTING.md`, "Platform reality").
- Maiores lacunas fora de janela: `CodexCliSessionsScreen.kt` 239, `ModalWindowsHost.kt` 203, `CollectorEngine.kt`
  197, `RemoteTeamDataSource.kt` 176, `AutoStartManager.kt` 175, `SettingsActions.kt` 143, `AppSettingsFeedback.kt`
  107, `AppGraph.kt` 106, `DashboardScreen.kt` 104, `DashboardScreenWarnings.kt` 102, `TelegramBotWiring.kt` 101.

## Decisões

- **Métrica:** linhas (`LINE`, `COVERED_PERCENTAGE`), a mesma que o relatório mostra. Ramos ficam no relatório,
  sem piso, até as linhas chegarem à meta.
- **Catraca, não piso fixo.** 95% hoje deixaria o CI vermelho em todo PR. O piso é o último valor medido,
  arredondado para baixo, e **só sobe**, no mesmo commit dos testes que o elevam: `coverageLineFloorPercent` no
  `build.gradle.kts` (passo `Coverage floor` do `ci.yml`, `koverVerify`) e `thresholds.lines` no
  `server/vitest.config.ts`. O relatório lê os dois pisos e mostra o portão "Meta de cobertura" (`crit` abaixo do
  piso, `warn` entre piso e meta).
- **Teste por contrato, nunca por linha.** Teste que só executa código sem afirmar comportamento não entra.
  Cada teste novo afirma rota, corpo, estado publicado, texto ou decisão — o mesmo padrão dos testes vizinhos.
- **Código de janela:** primeiro extrair a decisão para função pura e testá-la (precedente: `hudHitRegionMode`).
  O que sobrar de chamada direta ao AWT/JNA é candidato a exclusão **nominal** no filtro do Kover, como
  `MainKt`, com o motivo no `build.gradle.kts` e linha em "O que a suíte não cobre" do relatório. Exclusão é a
  última onda e é medida antes: nenhuma entra para fechar a conta.

- **Código de produção sem chamador de produção** (achado da onda 4, 2026-10-27): `DashboardScreen.kt` (104 linhas
  descobertas) e `ResponsiveDashboardCardGrid.kt` (55) só são chamados por testes e pelo gerador de capturas — o
  CLAUDE.md já registra que o `DashboardScreen` "só existe para testes e capturas". Cobri-los não protege o produto.
  Decisão adiada para a onda 6, com medição: exclusão nominal no Kover (como `MainKt`) ou mudança para `desktopTest`.

- **Rota escolhida pelo dono (2026-10-27): híbrido.** Lógica de decisão das janelas e hosts sai para função pura
  testada (precedente `hudHitRegionMode`) e host com lógica real ganha costura. Exclusão do Kover só por **lista
  nominal com motivo** no `build.gradle.kts` (como `MainKt`), e só para cola fina: `AppGraph`, `AppViewModels`,
  chamada direta a AWT/JNA sem decisão, e código de produção que só teste chama. O relatório lista o excluído em
  "O que a suíte não cobre".
- **Conta medida antes de excluir** (`build/cov/excl.py` sobre o relatório da onda 5): mesmo excluindo todo arquivo que
  importa janela, `AppGraph`, `AppViewModels` e o código só-teste, o Kotlin iria a **90,8%** — 1 790 linhas abaixo de
  95%. Exclusão não fecha a meta: o resto vem de teste.

## Ondas (uma PR cada)

| Onda | Escopo | Estado | Kotlin depois |
|---|---|---|---|
| 1 | Catraca; dados do time por `MockEngine`; servidor a ≥ 95% | PR #476, mesclada | 84,1% (servidor 95,8%) |
| 2 | Telas de sessões CLI/Codex, rótulos do Resumo, gráfico por resposta | PR #479, mesclada | 85,6% |
| 3 | Textos, rótulos e construtores puros; paridade de cores com `terminal.css` | PR #480, mesclada | 86,2% |
| 4 | Telas e view models com uso real (avisos, chaves, card, time, comparação, alertas) | PR #481, mesclada | 86,8% |
| 5 | Serviço/terminal/time testáveis sem a raiz de composição | PR #482, mesclada | 87,2% |
| 6.1 | Rota híbrida: exclusão nominal; geometria de janela numa implementação só | PR #483, mesclada | 88,1% |
| 6.2 | Costura em `AutoStartManager` (`AutoStartHost`), `SettingsActions`, `AppSettingsFeedback`, `buildShellActions` e leitores do bot; testes de `AppModalState` e `TelegramBotActions` | PR #485 | 89,5% |
| 6.3 | Janelas: extrair decisão de `HudWindow*`, `AppDialogWindow`, `DesktopWindowFrame`, `CompositorWindowDrag` para função pura; excluir só a chamada AWT/JNA que sobrar, por nome | PR #486 mesclada | 89,58% |
| 6.4 | Alavanca B da #488: teste de código com decisão fora de janela (terminal, TUI, bot, leitores locais, view models, telas) | em andamento | — |

Conta após a 6.1 (`build/cov/cats.py`): 5 407 linhas descobertas de 45 362; o teto para 95% é 2 268, faltam
**3 139**. Distribuição: `desktopMain` 2 598, janela 1 453, `commonMain` 1 327, gerado 22, só-teste 7.

Conta após a 6.2 (Kover XML): 4 745 linhas descobertas de 45 380; o teto para 95% é 2 269, faltam **2 476**. O que
sobrou nos arquivos da 6.2 é cola com o grafo: `TelegramBotWiring` 57 (as lambdas de `createTelegramBotService`),
`AutoStartManager` 56 (`RuntimeEnvironment.current`, `AutoStartHost.system`). Maiores lacunas agora: `HudWindow` 260,
`AppDialogWindow` 205, `CollectorEngine` 197, `ModalWindowsHost` 195, `SettingsWindowHost` 129.

## Atividades da onda 1

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano; catraca no Gradle, CI e vitest; portão no relatório; docs | `ci(coverage): ratchet line coverage toward 95%` |
| A02 | `RemoteTeamDataSource` por `MockEngine`: rota, credencial, query, corpo, erro | `test(team): cover every team server route over MockEngine` |
| A03 | Repositórios do time ponta a ponta por `MockEngine` | `test(team): cover team repositories end to end` |
| A04 | Servidor a ≥ 95% de linhas | `test(server): raise line coverage to 95%` |
| A05 | Piso do Kotlin sobe para o valor medido | `ci(coverage): raise the Kotlin line floor` |

## Atividades da onda 6.2

Lacunas de partida (Kover, arquivos sem mudança desde `af450570`): `AutoStartManager` 175, `SettingsActions` 143,
`AppSettingsFeedback` 107, `TelegramBotWiring` 101, `TelegramBotActions` 72, `AppShellActionsFactory` 72,
`AppModalState` 58 — 728 linhas. Nenhuma atividade monta `AppGraph` nem `AppViewModels` em teste.

| ID | Atividade | Commit esperado |
|---|---|---|
| W62-A01 | `AutoStartHost`: plataforma, pasta pessoal, `XDG_CONFIG_HOME`, executor e executável injetáveis; gravar, apagar e migrar nos três sistemas | `test(autostart): drive every platform through an injectable host` |
| W62-A02 | `AppModalState`, `TelegramBotActions` e `telegramBotSectionModel` por teste direto (já não dependem do grafo) | `test(shell): cover modal state and telegram bot actions` |
| W62-A03 | `buildShellActions` sobre uma porta (`ShellActionTargets`) em vez de `AppGraph`/`AppViewModels` | `test(shell): cover shell actions through a port` |
| W62-A04 | `SettingsActions` e `AppSettingsFeedback` sobre `AppSettingsStore` + portas; cliente HTTP de teste injetável | `test(settings): cover settings actions and feedback without the graph` |
| W62-A05 | Leitores do bot (`sourceHealthOf`, presença, gráfico) fora do `AppGraph` | `test(telegram): cover the bot readers without the graph` |
| W62-A06 | Medição e piso | `ci(coverage): raise the Kotlin line floor` |

## Atividades da onda 6.3

Partida medida em `origin/main` (`d8d6068e`), `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4 koverXmlReport`:
89,54% (40 635/45 380), 4 745 descobertas, bate com a 6.2. Janelas (linhas descobertas): `HudWindow` 260,
`AppDialogWindow` 205, `ModalWindowsHost` 195, `SettingsWindowHost` 129, `DesktopWindowFrame` 94,
`CompositorWindowDrag` 94, `HudWindowFrame` 67, `AppWindowStates` 57, `AppProfiles` 54, `AppPreferenceEffects` 54,
`HudWindowEffects` 52, `AppWindowAnchor` 52, `HudWindowTranslucency` 40.

Limite desta onda, medido antes de escrever código: a maior parte dessas linhas é corpo de `@Composable` e chamada
direta a `java.awt.Window`/JNA, que não tem decisão para extrair. Esta onda extrai só o que tem decisão testável;
o que sobra nas janelas não é excluído nesta onda — exclusão nominal exige a medição da onda seguinte e aprovação
do dono (regra do plano, "Decisões").

| ID | Atividade | Commit esperado |
|---|---|---|
| W63-A01 | Caminho até a janela cliente (`WM_STATE`) do `X11CompositorMove` sai do JNA para `compositorClientWindow`, sobre uma porta `X11WindowTree`; testado com árvore falsa (reparentagem, sem `WM_STATE`, consulta falha, profundidade, janela 0) | `test(window): cover the compositor client-window walk` |
| W63-A02 | Tamanho e posição pedidos à janela AWT da HUD viram `hudWindowTargetSize`/`hudWindowTargetLocation`, funções puras; `applyHudWindowBounds` só aplica o resultado | `test(hud): decide window bounds apart from the AWT call` |
| W63-A03 | Alternância maximizar/restaurar e rótulo do botão da barra saem de `DesktopDialogTitleBar` para funções puras | `test(window): decide the maximize toggle apart from the frame` |
| W63-A04 | Decisão de pedido de `ModalWindowBody` (`modalRequestAction`) e de pré-aquecimento (`modalPrewarmRequested`) como funções puras | `test(window): decide modal requests apart from the window` |
| W63-A05 | Medição, relatório e piso (sobe só se o valor medido chegar a 90) | `ci(coverage): raise the Kotlin line floor` |

## Atividades da onda 6.4 (issue #488, alavanca B)

Partida medida em `origin/main` (`7f379975`), `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4 koverXmlReport`:
3 598 testes, 0 falhas; **89,58%** (40 734/45 472), 4 738 descobertas; teto para 95%: 2 273, faltam **2 465**.
Distribuição (arquivo com import de janela = janela; com `@Composable` = composição; resto = lógica):
`desktopMain` lógica 1 531, janela 1 431, `commonMain` composição 669, `commonMain` lógica 658, `desktopMain`
composição 427, gerado 22. Dos 1 531 de lógica do `desktopMain`, `CollectorEngine` (204) é raiz de composição sobre
`AppGraph` (nunca montado em teste) e fica para a alavanca A, com os hosts de janela.

Cada atividade testa só linha com decisão (ramo, recusa, texto, mapeamento, estado publicado), lida no Kover XML antes
de escrever o teste; chamada direta a `System.console`, `systemctl` real ou cliente HTTP real fica de fora.

| ID | Atividade | Commit esperado |
|---|---|---|
| W64-A01 | `JLineTuiIo` sobre terminal JLine em memória: quadro desenhado (`CSI H`, `CSI K` por linha, `CSI J`), tecla com prazo, Ctrl+C como `Interrupt`, tamanho padrão quando o terminal diz 0, `close` idempotente devolve a tela | `test(tui): cover the JLine terminal over in-memory streams` |
| W64-A02 | Comandos do terminal: `service` (desinstalar sem unidade, controle com e sem falha, `logs --lines`), `analysis` (JSON e texto, sem conta), recusas do `telegram` e do `team`, `alerts`/`tmux`/JSON do `TerminalCommands` | `test(terminal): cover service, analysis, telegram and team refusals` |
| W64-A03 | Leitores da TUI: sessão ativa Claude e Codex, análise com índice fora da faixa, mapeamento da sessão Codex, períodos e exportação vazia | `test(tui): cover the active-session and Codex session readers` |
| W64-A04 | Bot do Telegram: limiares, confirmações, silêncio e lembrete nos dois idiomas; anéis/comparação sem dado e sem imagem; toques de botão | `test(telegram): cover thresholds, confirmations and image fallbacks` |
| W64-A05 | Leitores locais: recibo de atualização (ausente, ilegível, sem versão, status), uso por modelo do índice Codex, demais datasources com ramo descoberto | `test(data): cover update receipts and local index readers` |
| W64-A06 | View models: exportação da comparação (salva, falha, sem gravador), exportação do breakdown, créditos repetidos, falha de orçamento registrada uma vez; suportes do painel com ramo descoberto | `test(viewmodel): cover comparison export and session budget failures` |
| W64-A07 | Telas com ramo descoberto: `TeamUsageScreen`, `CliSessionsScreen`, `CliSessionDetailParts`, `TeamPresenceScreen`, `HistoryScreen`/`HistoryMetrics` | `test(ui): cover the remaining session, team and history screen states` |
| W64-A08 | Medição, relatório e piso (sobe para o valor medido arredondado para baixo) | `ci(coverage): raise the Kotlin line floor` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-27 | este commit | concluída | Medição acima. `node build-test-report.mjs --root <main>`: portões `coverage-goal-kotlin` e `coverage-goal-server` em `warn` (83,3% e 92,7%). Pisos: Kotlin 83, servidor 92. |
| A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.data.RemoteTeamDataSourceHttpTest"`: 28 testes, 0 falhas (todas as 22 rotas, credencial por tipo, query opcional, `PATCH` sem campo nulo, erro com JSON, HTML truncado em 300, corpo vazio). |
| A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.data.TeamRepositoriesHttpTest"`: 22 testes, 0 falhas (admin: chaves, bloqueio, visão, fallback 404; uso: leitura, detalhe, conexão, sem credencial não chama a rede). |
| A04 | 2026-10-27 | este commit | concluída | `npm run test:coverage`: 313 testes, 0 falhas; linhas **95,84%** (946/987), antes 92,7%. `ensureColumn` removida (sem chamador desde `859c819c`). Testes novos: health 503 e 500 com banco fechado, 6 validações de config, query/caminho inválidos em admin e relatório, fusão de modelos nas métricas. Piso do vitest: 95. |
| A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify -Pcoverage`: 3 346 testes, 0 falhas; Kotlin **84,1%** (antes 83,4%). Relatório da skill: servidor 95,8%, total 84,4%, sem `crit`. Piso do Kotlin: 84; `koverVerify` com 84 passa. |
| Onda 1 | 2026-10-27 | este commit | PR #476 | Run `40560244229`: `ci-ok` verde, 0 incidências; job `desktop-windows` executou `koverVerify` (`> Task :koverVerify`, `BUILD SUCCESSFUL`); `server` com o `thresholds.lines: 95`. |
| W2-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CodexCliSessionsScreenTest"`: 11 testes, 0 falhas (lista, EN, aba Resumo, faixa, detalhe, exportação salva e falha, índice parcial, vazio, erro com retry, rótulo de aplicação). |
| W2-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.BreakdownLabelsTest"`: 11 testes, 0 falhas (eixos, ordenação sem "custo" para ferramenta, paginação com filtro, custo com `+`, tempo ativo só medido e positivo, orçamento, créditos em moeda da conta, ritmo). |
| W2-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CliUsageBreakdownPaneTest"`: 6 testes, 0 falhas (carregando/erro, vazio, aba Ferramenta com "Chamadas" e sem "Custo", grade de atividade sem filtro, avisos de turno sem preço/projeção/leitura antiga, tempo ativo por integrante). |
| W2-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TurnSeriesChartTest"`: 4 testes, 0 falhas (tooltip por hover com todas as séries, "—" para não medido, saída esconde, compactação nomeada, empilhado, rótulo vazio). Hover exige `Enter` e depois `Move`. |
| W2-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CodexCliSessionsScreenTest"`: 13 testes, 0 falhas (+ PDF pelo mesmo gravador, falha de detalhe mantém a lista e mostra a causa). |
| W2-A06 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify`: 3 380 testes, 0 falhas; Kotlin **85,6%** (39 621/46 309; antes 84,1%), ramos 60,2%. Relatório: sem `crit`; servidor 95,8%. Piso do Kotlin: 85. |
| W3-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HistoryScreenFormattingTest"`: 11 testes, 0 falhas (título/subtítulo por tipo de fonte, previsões, DeepSeek, unidades, grupos OpenCode e genérico, rótulo de janela, sobreposição só em percentual, comparação com sinal). |
| W3-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.TeamUsageLabelsTest"`: 8 testes, 0 falhas (título por conta, plural, tendência, colunas herdadas, janela vazia por tipo, avisos de remoção, último envio). |
| W3-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.terminal.TerminalColorsTokenParityTest"`: 4 testes, 0 falhas. `TerminalColors` comparado com `docs/design-system/tokens/terminal.css` (escuro, claro, seleção) e com o mapa ANSI 16 documentado; toda `ApiSource` tem token. |
| Onda 2 | 2026-10-27 | este commit | PR #479 (merge `32910d9e`) | Run `40572860241`: `ci-ok` verde, 0 incidências; passo `Coverage floor` executou `koverVerify` com piso 85. |
| W3-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.terminal.TerminalServiceAndConfigLabelsTest"`: 5 testes, 0 falhas (quem já coleta, ciclo do serviço, systemd com e sem detalhe, onde a mudança vale, texto distinto por tipo de valor). |
| W3-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.CliSessionsLabelsTest"`: 6 testes, 0 falhas (título por perfil, custo estimado por tipo de janela, vazio ancorado × deslizante, saúde, contagem só do que pede ação, avisos). |
| W3-A06 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.TeamPresenceLabelsTest"`: 4 testes, 0 falhas (título da visão global, sinal/turno ausentes, plural, remoção e relógio divergente). |
| W3-A07 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.UsageExportRequestsTest"`: 5 testes, 0 falhas (nome por escopo/faixa/formato com o dia em BRT na virada UTC, relatório com idioma, mensagens de resultado). |
| W3-A08 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.GetTeamUsageTrendUseCaseTest"`: 3 testes, 0 falhas (eixo de dias termina hoje em BRT e inclui dias sem atividade, servidor sem a rota dá `null`, falha continua falha). Use case estava em 0%. |
| W3-A09 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ComparisonReportBuilderTest"`: 2 testes, 0 falhas (PDF da comparação com a mesma célula da tela, custo incompleto com `+`, Codex "sem tarifa", uma coluna por métrica). Builder estava em 0%. |
| W3-A10 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify`: 3 428 testes Kotlin, 0 falhas; Kotlin **86,2%** (39 939/46 309; antes 85,6%), ramos 61,2%. Relatório: 3 741 testes, sem `crit` (o `warn` de resultado atual é o JUnit do servidor, sem mudança desde a onda 1). Piso do Kotlin: 86. |
| W4-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.DashboardWarningsLanguageTest"`: 4 testes, 0 falhas (12 banners com título em inglês e português distintos e o mesmo botão nos dois idiomas, retry universal só na conectividade, prazo do 429 em inglês, falha comum sem banner). |
| W4-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamKeysAdminScreenTest"`: 13 testes, 0 falhas (+4: renomear salva e cancelar descarta, chave revogada marcada e sem novo "Revogar", falha de leitura com retry, rótulos em inglês). |
| W4-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ApiUsageCardTextsTest"`: 5 testes, 0 falhas (reset em inglês por janela, reset curto omitido sem dado, risco e explicações, tooltip por unidade, símbolos de moeda). |
| Onda 3 | 2026-10-27 | este commit | PR #480 (merge `05e2aefa`) | Run `40574945536`: `ci-ok` verde, 0 incidências; `Coverage floor` executou `koverVerify` com piso 86. |
| W4-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.TeamUsageViewModelTest"`: 52 testes, 0 falhas (+6: tendência entra no estado, falha da tendência sem erro na tela, PDF sai do estado sem recarregar, falha de gravação, sem gravador nada acontece, janela de quota repetida não recarrega). |
| W4-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ComparisonScreenTest"`: 3 testes, 0 falhas (+2: carregando e erro com retry em inglês, recorte vazio e resultado da exportação salvo/falho). |
| W4-A06 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.AlertSettingsSectionTest"`: 13 testes, 0 falhas (+4: limiar próprio por conta em ordem de rótulo e desfeito pelo botão, aviso sem limiar, lembrete grava os minutos padrão, validação de dólares com vírgula e `wrapHour`). |
| W4-A07 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify`: 0 falhas; Kotlin **86,8%** (40 193/46 309; antes 86,2%), ramos 61,6%. Piso continua 86 (valor medido arredondado para baixo). |
| W5-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.CollectorHealthReportTest"`: 5 testes, 0 falhas (contas coletadas com a falha e só-falha também listadas, estado do bot por palavra, falha do time só se mais nova que o sucesso, web sem token). `collectorHealthOf` estava em 0%. |
| W5-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ObservedUsageSummaryTest"`: 6 testes, 0 falhas (vazio por fonte com orientação própria, Gemini fala de tokens, uma linha por modelo, nota de cota separada no Gemini, linha compacta, trilha por janela). Bloco é usado pelo `ApiUsageCard` em produção. |
| W5-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.data.TelegramBotApiTest"`: 18 testes, 0 falhas (+4: `answerCallbackQuery` com id e texto, `getMe`, 502 com corpo ilegível, `ok:false` com descrição). |
| W5-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.TeamKeyAdminUseCasesTest"`: 1 teste, 0 falhas (repasse de cada caso de uso com defaults e `trim` do rótulo). |
| W5-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.terminal.TerminalServiceViewTest"`: 5 testes, 0 falhas (cabeçalho por quem coleta e código 1 sem coletor, pid/memória só com processo vivo, linhas de fontes/histórico/bot/time/web, estados quietos, saúde antiga avisa). |
| Onda 4 | 2026-10-27 | este commit | PR #481 (merge `18e97d51`) | Run `40576390227`: `ci-ok` verde, 0 incidências; `Coverage floor` executou `koverVerify` com piso 86. |
| W5-A06 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.UsageAlertMessagesTest"`: 9 testes, 0 falhas (+2: limiar de 100% diz "esgotada" e reset só quando conhecido; lembrete de reinício em inglês e português). |
| W5-A07 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify`: 0 falhas; Kotlin **87,2%** (40 363/46 309; antes 86,8%), ramos 62,1%. Restam 5 946 linhas descobertas (teto para 95%: 2 315): `desktopMain` 2 778, janela 1 661, `commonMain` 1 326, só-teste 159, gerado 22. Piso do Kotlin: 87. |
| Onda 5 | 2026-10-27 | este commit | PR #482 (merge `6140a0ee`) | Run `40577863115`: `ci-ok` verde, 0 incidências; `Coverage floor` executou `koverVerify` com piso 87. |
| W6-A01 | 2026-10-27 | este commit | concluída | Exclusão nominal aprovada pelo dono (rota híbrida): `AppGraph`, `AppViewModels`, `DashboardScreenKt`, `ResponsiveDashboardCardGridKt` (+ lambdas e `ComposableSingletons`). `codexProfileSources` (`AppGraphKt`) e 3 classes privadas da grade seguem contadas. `gradlew.bat koverXmlReport -Pcoverage` sobre a passada da onda 5: **87,7%** (40 003/45 617; antes 87,2%). Relatório lista os excluídos em "O que a suíte não cobre". |
| W6-A02 | 2026-10-27 | este commit | concluída | As 4 cópias de `*WindowPreferences.kt` (671 linhas, ~230 descobertas) viram `WindowGeometryPreferences.kt` (uma implementação, `WindowGeometrySpec` por janela, chaves gravadas idênticas) e o `PersistAppWindowStates` perde os 4 blocos repetidos. `gradlew.bat desktopTest --tests WindowGeometryPreferencesTest --tests ArchitectureRulesTest --tests TeamPresenceScreenTest`: 8 + 17 + 31 testes, 0 falhas. Chaves e tamanhos padrão travados por teste. Testado só no Windows; o comportamento de janela não muda (só a persistência foi unificada). |
| W6-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport koverVerify`: 0 falhas; Kotlin **88,1%** (39 955/45 362; antes 87,2%), ramos 62,9%. Restam 5 407 descobertas (teto para 95%: 2 268): `desktopMain` 2 598, janela 1 453, `commonMain` 1 327. Piso do Kotlin: 88. |
| Onda 6.1 | 2026-10-27 | este commit | PR #483 (merge `f7f6ae58`) | Run `40583073069`: `ci-ok` verde (inclui `desktop-linux`), 0 incidências; `Coverage floor` executou `koverVerify` com piso 88. |
| W62-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.AutoStartManager*" --tests "com.usagemonitor.architecture.*"`: 26 + 32 + 17 testes, 0 falhas. Linux grava/apaga `.desktop` (XDG e pasta pessoal), migra argumento, árvore versionada e entrada quebrada; macOS grava plist com `launchctl load`/`unload`; Windows `query`/`import`/`delete`; `OTHER` recusa. Achado: `currentPlatform` testava `win` antes de `darwin` (que contém `win`), ramo `darwin` inalcançável; ordem trocada, sem efeito em OpenJDK (`Mac OS X`). Leitura de `reg query` unificada em `parseRegistryQueryValue`. |
| W62-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.TelegramBotActionsTest" --tests "com.usagemonitor.AppModalStateTest"`: 9 + 5 testes, 0 falhas. Bot: cada ação publica e grava no `telegram.json`, código de pareamento pelo TTL a partir do relógio, controle/só leitura por id, trocar a hora religa o resumo, falha de gravação não trava a tela, teste vai a cada conversa pareada (`MockEngine`), rótulo e tom de cada estado, código expirado some. Modais: geração sobe a cada abertura, assunto gravado por quem abre. |
| W62-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.AppShellActionsFactoryTest"`: 8 testes, 0 falhas. `buildShellActions` recebe `BreadcrumbRecorder`, a porta `ShellActionTargets` (implementada pelo `AppViewModels`, já excluído do Kover) e o `UsageExportWriter`, em vez de `AppGraph`/`AppViewModels`; o `AppShellState` sem uso saiu da assinatura. Afirmado: nome de cada tela na trilha, sem e-mail; visões de admin só com `isAdminMode`; conta sem `accountUuid` não abre nada; janela de 5 h da leitura; sincronização antecipada; nome do CSV no dia de BRT; falha da exportação na trilha. |
| W62-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.SettingsActionsTest" --tests "com.usagemonitor.AppSettingsFeedbackTest" --tests "com.usagemonitor.TelegramBotActionsTest"`: 23 + 10 + 9 testes, 0 falhas. `SettingsActions` e `AppSettingsFeedback` recebem `AppSettingsStore`, `BreadcrumbRecorder` e portas (`SettingsActionTargets`, implementada pelo `AppViewModels`; vínculo e token como funções; `httpClientFactory` para o `MockEngine`; `AutoStartHost`, raiz de dados e seletores de pasta) em vez de `AppGraph`/`AppViewModels`. Afirmado: autostart grava o estado real e nunca fora da raiz padrão; relatório de bug só depois do esmaecimento; chave aparada, apagar desliga a fonte, falha não publica e a chave nunca vai à trilha; contas Anthropic/Codex com motivo de recusa; apelido gravado não se apaga; proxy só limpa o teste ao mudar endereço; os quatro testes de conexão com OK, HTTP, exceção e conta que falhou. Bloquear a gravação exige pasta **não vazia** (`Files.move` com `REPLACE_EXISTING` substitui pasta vazia); o teste do bot da A02 foi corrigido para isso e agora afirma que nada foi gravado. |
| W62-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.TelegramBotWiringTest"`: 9 testes, 0 falhas. `sourceHealthOf`, `teamPresenceReader`, `comparisonReader`, `accountHistoryReader` e `telegramChartOf` recebem o caso de uso e os provedores de que precisam, não o `AppGraph`; o `language` sem uso saiu de `sourceHealthOf`. Afirmado: saúde desconhecida antes da 1ª leitura, falha com a hora da leitura mantida e rótulo da fonte quando vazio; presença pergunta cada conta uma vez, junta pessoas e só falha se todas falham; comparação com as cotas da última coleta; histórico sem a conta ilegível; gráfico numera contas na ordem das fontes, sem e-mail; sem ponto não há gráfico. |
| W62-A06 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4 koverXmlReport` + `koverVerify -Pcoverage`: 3 566 testes, 0 falhas; Kotlin **89,5%** (40 635/45 380; antes 88,1%), ramos 63,8%. Relatório da skill: 17 regras de arquitetura ok, sem `crit` (servidor não medido nesta passada: não mudou). Piso do Kotlin: 89. Testado só no Windows; nenhum host de janela mudou (só a fiação em `Main.kt` e `AppViewModels.kt`). |
| Onda 6.2 | 2026-10-27 | este commit | PR #485 | Run `40587994103`: `ci-ok` verde (inclui `desktop-linux`), 0 incidências; `Coverage floor` executou `koverVerify -Pcoverage` com piso 89. |
| W62-A07 | 2026-10-27 | este commit | concluída | Incidência 1 (run `40588953915`): `desktop-linux` (consultivo, fora do `ci-ok`) falhou em 2 testes do `AppSettingsFeedbackTest` (linhas 145 e 199): o fake respondia na hora e a corrotina no `Dispatchers.Default` terminava antes de o teste ler `CHECKING`. Correção no `SettingsTestHarness`: as respostas falsas (servidor, vínculo, token, `MockEngine`) esperam uma trava liberada pelo `settle()`. `gradlew.bat desktopTest --rerun --tests "com.usagemonitor.AppSettingsFeedbackTest" --tests "com.usagemonitor.SettingsActionsTest"` 3×: 10 + 23 testes, 0 falhas em cada. |
| W63-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.CompositorClientWindowTest" --tests "com.usagemonitor.architecture.*"` (rodada combinada com as suítes vizinhas de janela): `CompositorClientWindowTest` 9 testes, 0 falhas; `BUILD SUCCESSFUL`. A subida pela árvore X11 saiu do JNA para `compositorClientWindow`, sobre a porta `X11WindowTree`. Testado só no Windows. |
| W63-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowBoundsTargetTest"` (rodada combinada, ver W63-A01): 7 testes, 0 falhas. `hudWindowTargetSize` e `hudWindowTargetLocation` são puras; `applyHudWindowBounds` só aplica o que elas devolvem, com a mesma ordem de `moveProbe.requested` e a mesma checagem de igualdade. Testado só no Windows; janela da HUD não é aberta em teste. |
| W63-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.MaximizeToggleTest"` (rodada combinada, ver W63-A01): 2 testes, 0 falhas. `maximizeToggled` e `maximizeButtonLabel` saíram de `DesktopDialogTitleBar`; o clique e o glifo leem as mesmas funções. Testado só no Windows. |
| W63-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ModalRequestActionTest" --tests "com.usagemonitor.AppDialogWindowTest"` (rodada combinada, ver W63-A01): `ModalRequestActionTest` 5 testes, `AppDialogWindowTest` 7 testes, 0 falhas. `modalRequestAction` e `modalPrewarmRequested` são puras; `ModalWindowBody` troca o `if` pelo `when` sobre a decisão, sem mudar a ordem das chamadas em cada ramo. Testado só no Windows; a janela modal não é aberta em teste. |
| W63-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun -Pcoverage -PtestForks=4 koverXmlReport koverVerify`: `BUILD SUCCESSFUL`, 3 589 testes, 0 falhas; Kotlin **89,58%** (40 668/45 400; antes 89,54%). Piso continua 89 (valor medido arredondado para baixo, não sobe). Ganho da 6.3: 33 linhas. Resto nas janelas (`AppDialogWindow` 203, `DesktopWindowFrame` 91, `CompositorWindowDrag` 86, `HudWindowFrame` 67) é corpo de `@Composable` e chamada direta a AWT/JNA, sem decisão nova para extrair. Alavancas que faltam exigem decisão do dono: exclusão nominal do AWT/JNA residual (medir antes) ou teste de composable por `runDesktopComposeUiTest`. |
| Onda 6.3 | 2026-10-27 | este commit | PR #486 | Run `40611029263`: `ci-ok` verde (inclui `desktop-linux`, consultivo, e `desktop-windows` com `koverVerify -Pcoverage`, piso 89), 0 incidências. Testado no Linux só pelo CI; a conferência manual no COSMIC fica em aberto. |
| W64-A01 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun --tests "com.usagemonitor.terminal.JLineTuiIoTest"` 3×: 8 testes, 0 falhas em cada. `JLineTuiIo` sobre `ExternalTerminal` em memória (construtor passa a `internal`; o app segue entrando só por `open`): tela alternativa e cursor escondido com a terminfo e com a sequência do xterm quando a terminfo não descreve (`dumb`), modo cru, quadro exato (`CSI H`, `CSI K` por linha, `CSI J`, com OPOST desligado), 80×24 quando o terminal diz 0, tecla decodificada, prazo vencido dá `null`, Ctrl+C pelo ISIG vira `Interrupt`, mudança de tamanho sem SIGWINCH para a espera e é avisada uma vez, `close` devolve a tela e fecha o terminal uma vez só. |
| W64-A02 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun --tests "com.usagemonitor.terminal.*"`: 163 testes, 0 falhas (`TerminalServiceCommandsTest` 14, `TerminalServiceCommandLineTest` 4, `TerminalAnalysisCommandTest` 7, `TerminalTelegramCommandTest` 10, `TerminalTeamCommandTest` 6, `TerminalCommandsTest` 13). Afirmado: `service uninstall` recusa sem unit e senão desliga, apaga e recarrega, nessa ordem; `start/stop/restart` repetem a recusa do systemd; `install` para sem systemd do usuário (primeira linha da causa + linger), guarda a unit e avisa quando o `enable` falha, avisa do app coletando e omite o linger já ligado; `status` nomeia a instalação por script e esconde a memória de unit parada; `logs` sem `--follow`; `XDG_CONFIG_HOME` em branco cai em `~/.config`; parser de `service logs` (`-f`, `--lines` 1–10000). `analysis` lê o cache de verdade (JSON e texto), filtro sem conta não abre banco e sai com 3, JSON em ASCII escapa acento. `telegram`: nada digitado, Telegram fora do ar, `test` sem token/sem conversa/envio falho, `pair` sem token com coletor, código vencido pelo `--timeout 1s`, pareamento da TUI sem esperar. `team join` pede servidor, apelido e chave nessa ordem sem gravar; `--skip-check` diz que não conferiu e o segundo `join` reusa servidor e apelido. `alerts` hoje × `--since`, `tmux` sem ANSI, `status --json` sem leitura (código 3) e em ASCII, `check` pelo teto. Nenhuma mudança em produção. |
| W64-A03 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.terminal.TuiReaderTest" --tests "com.usagemonitor.terminal.TuiSessionsReaderTest"`: 7 + 5 testes, 0 falhas. Índices de verdade (`ClaudeTranscriptFixture`, `CodexRolloutFixture`, compartilhadas na pasta de teste; o `TuiSessionsReaderTest` passa a usar a do Claude Code). Afirmado: sessão ativa do Claude Code nos últimos 10 min com projeto, modelo e custo, e `NONE` 30 min depois; Codex padrão com sessão ativa sem custo e sem marca de incompleto, e só na Visão geral; aba de análise prende a conta escolhida às elegíveis (9 → 1, −3 → 0) e sem bancos não analisa; sessão do Codex separa a entrada em cache, não tem custo, dura do primeiro ao último registro de uso e abre os turnos sem custo; índice do Claude Code ausente não esconde o Codex; corte de 30 dias × 7 dias; exportação sem sessão e com índice ausente. Nenhuma mudança em produção. |
| W64-A04 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --rerun --tests "com.usagemonitor.TelegramBotServiceTest" --tests "com.usagemonitor.presentation.TelegramBotMessagesTest" --tests "com.usagemonitor.presentation.TelegramBotRemoteMessagesTest"` 3×: 51 + 18 + 6 testes, 0 falhas em cada. Mensagens: limiares gerais com alerta de cota desligado e por conta em ordem de rótulo (escapado), confirmação de silêncio/limiar/lembrete/alertas nos dois idiomas, comando sem efeito responde a ajuda, teclado e silêncio em inglês, cota sem percentual (saldo, usado/total, só usado); fontes sem permissão com o estado de cada uma, fonte ligada/desligada, silêncio até hoje × amanhã, foco, fonte quebrada com e sem leitura guardada, pergunta e resposta do limiar geral e por conta. Handlers: `/status img` sem cota em percentual responde em texto e com cota sai foto; `/vazao` e `/comparar` sem índice dizem que não leram; botão "Limiares" e botão desconhecido; resposta de limiar inválida pede o formato e a próxima grava; toque de limiar em conta que saiu do retrato não abre pergunta; `/analise` com duas contas pergunta qual e o placar fica só com quem tem histórico; sem conta analisável, sem histórico e toque em conta sumida; sem Skia a imagem vira o painel em texto e o relatório sai em PDF. **Achado:** o teste `without Skia the chart and the status image answer in text` mandava `/img`, que é `BotCommand.Invalid` (o comando é `/status img`), e passava sem exercitar a imagem; corrigido para `/status img` com leitura em percentual, e agora afirma que o status sai em texto e que nada caiu no "Não entendi". Nenhuma mudança em produção. |
| W64-A05 | 2026-10-27 | este commit | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.update.UpdateReceiptReaderTest" --tests "com.usagemonitor.data.LocalAnthropicCreditsDiagnosticsRecorderTest" --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest" --tests "com.usagemonitor.data.LocalCodexCliSessionDataSourceTest"`: 5 + 2 + 64 + 5 testes, 0 falhas. Recibo de atualização (estava em 0%): sucesso completo com versão anterior aparada, status desconhecido é falha (o lado seguro), sem arquivo/pasta no lugar/sem versão não é recibo, recibo de falha do `schedule` gravado no formato do instalador sem `previousVersion` e relido, gravação impossível descartada sem exceção. Diagnóstico de créditos (estava em 0%): desligado não cria nada; ligado anexa uma linha JSON por coleta e cria a pasta. Vazão por modelo do índice do Claude Code: soma tokens e tempo ponta a ponta por modelo, deixa fora a resposta sem pedido, respeita corte e conta. Uso por modelo do Codex: soma por modelo e mede a vazão só na resposta com duração. Nenhuma mudança em produção. |
