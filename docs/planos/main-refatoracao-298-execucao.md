# Quebrar `runUsageMonitor` e impor regras de arquitetura (#298) — execução

## Contexto

[Issue #298](https://github.com/edilsonvilarinho/usage-monitor/issues/298): `runUsageMonitor`
(`Main.kt`) é um lambda `application {}` de ~2.400 linhas com a DI manual e o estado de todas as
janelas. O `CLAUDE.md` já registrava o `OutOfMemoryError` do ASM ao transformar o método inteiro. O
comentário do dono na issue amplia o pedido: nada de "super classes", as regras de clean code e de
arquitetura gravadas no `CLAUDE.md` e no `AGENTS.md`, e a arquitetura sempre respeitada.

Escopo aprovado:
- `Main.kt`;
- as violações de camada;
- um teste de arquitetura que imponha as regras.

Os demais arquivos grandes viram issues próprias.

## Fatos de partida

- `runUsageMonitor` tem 2.393 linhas pela varredura de chaves. Outras cinco funções de produção passam
  de 300 linhas:

  | Função | Linhas |
  |---|---|
  | `ApiUsageCard` | 483 |
  | `UsageHistoryLineChart` | 460 |
  | `HudNotch` | 348 |
  | `HudWindowHost` | 346 |
  | `TeamUsageList` | 327 |

- 16 arquivos de produção passam de 800 linhas.
- Havia 8 imports de `presentation` para `data`, contrariando `presentation → domain ← data`:
  - `UsageExportFormat`, em 5 arquivos;
  - `UsageExporter` e `CodexCliUsageExporter`, em `UsageExportRequests`;
  - `UPDATE_FEED_URL_ENV_VAR`, em `SettingsDialogContent`.
- O domain não importava infraestrutura nem outra camada. Havia só um link de KDoc para um DTO de
  `data`.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| M0 | Linha de base de compilação (local, daemon quente, 16 processadores) | `gradlew.bat compileKotlinDesktop --rerun-tasks --no-build-cache -Pkotlin.build.report.output=file` | 18,46 s de tarefa: análise 4,99 s, tradução IR 2,84 s, geração 8,71 s (lowering 2,48 s, geração IR 6,23 s), 71.269 linhas |
| M1 | Camadas: `UsageExportFormat` → `domain/entity`; porta `UsageExportEncoder` no domain, implementada por `DefaultUsageExportEncoder` em `data` e injetada nos dois view models de sessões e no retrato do Dashboard; `UPDATE_FEED_URL_ENV_VAR` → `domain/repository/AppUpdateRepository.kt`; link de KDoc do domain para `data` virou texto | `gradlew.bat desktopTest --tests "com.usagemonitor.data.*Export*" --tests "com.usagemonitor.presentation.CliSessionsViewModelTest" --tests "com.usagemonitor.presentation.CodexCliSessionsViewModelTest" --tests "com.usagemonitor.ui.CliSessionsScreenTest" --tests "com.usagemonitor.ui.SettingsDialogContentTest" --tests "com.usagemonitor.data.AppUpdateRepositoryImplTest"` | 146 testes, 0 falhas; zero imports de `data` em `presentation` e em `domain` |
| M2 | `ArchitectureRulesTest` + `KotlinSourceScanner` (desktopTest, sem dependência nova): direção entre camadas por import, arquivo ≤ 800 linhas, função ≤ 300 linhas por varredura de chaves sem comentários e strings; 16 arquivos e 6 funções congelados no tamanho atual, com o teto exato (crescer falha, encolher pede baixar o teto). Contagem normaliza CRLF: com `sed` do Git Bash o arquivo virou LF e as linhas em branco finais deixavam de contar | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*"` | 6 testes, 0 falhas. Teste negativo (import de `data.export` injetado em `HistoryScreen.kt`, depois revertido): `presentation nao importa data` falhou apontando o arquivo e o import |
| M3–M6 | `runUsageMonitor` quebrado em 16 arquivos (`AppGraph`, `AppViewModels`, `AppShellState`, `AppModalState`, `AppSettingsFeedback`, `SettingsActions`, `SettingsWindowHost`, `AppWindowStates`, `AppPreferenceEffects`, `AppShellActionsFactory`, `AppTrayHost`, `AppStartup`, `MainWindowHost`, `ModalWindowsHost`, `AppPreferenceKeys`, `AppProfiles`), todos ≤ 387 linhas; `runUsageMonitor` 2.395 → 187 linhas e `Main.kt` 3.141 → 329, fora das duas listas de exceção. **Desvios do plano:** (1) M3–M6 feitos numa passada só na árvore, sem commits intermediários, e por isso entram num commit só; (2) os arquivos ficaram no pacote `com.usagemonitor`, não em `.app`, para não abrir a visibilidade de dezenas de helpers `private`/`internal`; (3) os nomes previstos (`AppPreferencesState`, `StartupEffects`, `UpdateHost`) não existem — a divisão real é a lista acima. **Mudança de comportamento intencional:** as três cópias do encerramento viraram `AppViewModels.shutdown()`, com o superconjunto — antes o shutdown hook não fechava o índice do Codex e a saída pela janela não fechava o `profileRegistry` | `gradlew.bat allTests` | 2.217 testes, 0 falhas (219 classes). Verificação manual no app **pendente**: exige fechar o app instalado, senão o `gradlew run` vira segunda instância e sai |
| M7 | Medida depois da quebra, mesmo comando de M0; depois o build sem `kotlin.daemon.jvmargs` **e** sem `org.gradle.jvmargs` (sem a segunda o daemon Kotlin herda os 3 GB do Gradle e o teste não mediria nada), daemons derrubados antes | `gradlew.bat compileKotlinDesktop --rerun-tasks --no-build-cache -Pkotlin.build.report.output=file` | Com `-Xmx3g`: 17,41 s de tarefa — análise 4,82 s, tradução IR 2,96 s, geração 7,63 s (lowering 2,31 s, geração IR 5,32 s), 71.733 linhas. Uma amostra de cada lado: a diferença de ~1 s contra M0 não é afirmada como ganho. Sem as duas linhas: `BUILD FAILED`, `OutOfMemoryError: GC overhead limit exceeded` em `Couldn't transform method node: BugReportHost` (função de ~165 linhas). **A folga continua**: o que falta é heap para o módulo, não para um método gigante. O comentário do `gradle.properties` foi corrigido; a atribuição ao `main()` deixou de ser verdadeira |
