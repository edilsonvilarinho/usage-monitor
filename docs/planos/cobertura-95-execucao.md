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

| Onda | Escopo | Linhas descobertas no escopo |
|---|---|---|
| 1 | Catraca; dados do time (`RemoteTeamDataSource`, repositórios, mapper) por `MockEngine`; servidor a ≥ 95% | ~400 + 23 |
| 2 | Telas Compose de sessões CLI/Codex (`CodexCliSessions*`, `CliUsageBreakdown*`, `ActivityHeatmapGrid`, `TurnSeriesChart`) | ~680 |
| 3 | Formatação e rótulos puros (`*Formatting.kt`, `*Labels.kt`, `*Messages.kt`, terminal) | ~500 |
| 4 | Telas Compose restantes (`DashboardScreen*`, `TeamKeysAdminScreen`, `TeamUsageScreen`, cards) | ~600 |
| 5 | Orquestração do `desktopMain` (`CollectorEngine`, `SettingsActions`, `AppSettingsFeedback`, `AutoStartManager`, Telegram, terminal) com costura testável | ~1 500 |
| 6 | Janelas: extração de decisão pura; exclusão nominal do resto, medida | ~1 700 |

- **Raiz de composição** (achado da onda 5, 2026-10-27): `AppGraph`, `CollectorEngine` e `AppViewModels` (~380 linhas)
  só se montam lendo as credenciais reais (`~/.claude`), o nó de preferências do sistema e abrindo clientes HTTP de
  verdade. Teste que os monte leria segredo do desenvolvedor e faria chamada de rede real — recusado. Ou ganham
  costura (fábricas injetáveis), ou entram na exclusão nominal da onda 6, como o `MainKt`.

## Atividades da onda 1

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano; catraca no Gradle, CI e vitest; portão no relatório; docs | `ci(coverage): ratchet line coverage toward 95%` |
| A02 | `RemoteTeamDataSource` por `MockEngine`: rota, credencial, query, corpo, erro | `test(team): cover every team server route over MockEngine` |
| A03 | Repositórios do time ponta a ponta por `MockEngine` | `test(team): cover team repositories end to end` |
| A04 | Servidor a ≥ 95% de linhas | `test(server): raise line coverage to 95%` |
| A05 | Piso do Kotlin sobe para o valor medido | `ci(coverage): raise the Kotlin line floor` |

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
