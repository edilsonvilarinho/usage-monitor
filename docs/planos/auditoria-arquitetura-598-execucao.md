# Auditoria de arquitetura — issue #598

Procedência: Claude Opus 5.5 (orquestração), agentes de implementação conforme `.claude/agents/`, Claude Code CLI no
Windows 11, 2026-11-14, branch `fix/issue-598-architecture-audit`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Corrigir os desvios das regras de arquitetura do `CLAUDE.md` (camadas da Clean Architecture, DI manual, primitivas
visuais, contrato de `UiState`) que o `ArchitectureRulesTest` não cobre, e transformar cada regra corrigida em teste,
para que ela deixe de depender de auditoria manual.

## Fatos confirmados (2026-11-14, `acac74cd`)

- `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*"` → 17 testes, 0 falhas. Regras já impostas: camadas
  por import, tamanho de arquivo e função, recorte de janela, raiz de dados, idioma inline, `toString` de segredo.
- 18 contratos `interface *Repository` só em `domain/repository`; 37 use cases, todos com `operator fun invoke`.
- Nenhum `System.getenv` lê chave de API; nenhum `AlertDialog`; todo `TimeZone.of` é `America/Sao_Paulo`; todo
  `CoroutineScope` de view model tem `SupervisorJob`; nenhum arquivo de produção entre 750 e 800 linhas (máx. 743).
- `runCatching` em 13 `*RepositoryImpl` de `commonMain/data/repository`; só `CodexRepositoryImpl` cita
  `CancellationException` (varredura `grep -c` por arquivo). `Cursor`/`Gemini`/`Antigravity` usam `try/catch` que relança.
- Heurística awk (propriedade de classe `@Serializable` sem `@SerialName` na linha ou na anterior) em `data/`: ~340
  propriedades em 23 arquivos; `TeamDto.kt` 132 (1 `@SerialName` no arquivo). Nenhum `JsonNamingStrategy` no projeto.
- `Main.kt:293,343,346` monta `GetReleaseNotesUseCase`, `ClaimTeamKeyForAccountUseCase`, `ValidateAdminTokenUseCase`;
  `AppWindowAnchor.kt:101` monta `GenerateBugReportUseCase` dentro de `remember`.
- `Surface(color = MaterialTheme.colorScheme.background)` como raiz em `CliSessionsScreen.kt:164`,
  `TeamUsageScreen.kt:249`, `TeamKeysAdminScreen.kt:101`, `help/HelpContent.kt:110`, `help/OnboardingGuide.kt:164`,
  `help/StartupTipContent.kt:62`, `DesktopWindowFrame.kt:120`. `AppWindowScaffold` (`AppStructure.kt:96`) não é
  equivalente: pinta com `Modifier.background` (sem `LocalContentColor`) e aplica padding.
- `CliSessionDetailUiState` e `TeamSessionDetailUiState` usam `Ready` (38 ocorrências em 11 arquivos);
  `ApiKeyCheck.kt:21`, `NetworkSettingsSection.kt:24`, `TeamIntegrationSection.kt:27` declaram `data class *UiState`.

## Decisões

- **Fora do escopo, por serem intencionais:** `DashboardViewModelDefaults.kt` (Null Object quando a build não liga a
  fonte); `AnalysisUiState` sem `Error` (falha mantém a análise anterior com `liveFailedAt`, regra "leitura que falha
  mantém os números"); raízes de composição do modo terminal e do bot Telegram (rodam sem a interface gráfica);
  `defaultScreenDevice` em diagnóstico, fallback do `ScreenLocator` e checagem de translucidez.
- **A02 é hipótese:** começa pelo teste que cancela a coleta durante a requisição. Se o teste nascer verde, a hipótese
  cai, a linha registra a execução e nada muda em produção.
- **`@SerialName` com o nome atual da propriedade:** o JSON não muda (sem estratégia de nomes, a chave já é o nome
  Kotlin). A anotação congela o contrato contra renomeação.
- **Toda regra corrigida vira teste no `ArchitectureRulesTest`**, vermelho antes da correção.
- **Uma branch, atividades sequenciais:** as atividades tocam arquivos que compilam juntos; agentes em paralelo no
  mesmo checkout misturariam vermelhos.

## Atividades

| ID | O quê |
|---|---|
| A01 | Este plano |
| A02 | Repositórios: cancelamento propaga em vez de virar `Result.failure` (hipótese, teste primeiro) |
| A03 | `@SerialName` em toda propriedade `@Serializable` de `data` + regra no `ArchitectureRulesTest` |
| A04 | Use cases de `Main.kt` e `AppWindowAnchor.kt` montados pelo `AppGraph` + regra |
| A05 | Primitiva de raiz de tela no lugar de `Surface(color = background)` + regra + design system |
| A06 | `Ready` → `Success` nos `UiState` de detalhe; `data class *UiState` de formulário → `*FormState` |

## Ponto de situação

| ID | Situação | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos, decisões e atividades | `gh issue create` → #598; `git worktree add … origin/main` (`acac74cd`) |
| A02 | Hipótese confirmada: `runCatching` devolvia o cancelamento como `Result.failure`. `suspendRunCatching` (`data/repository/SuspendRunCatching.kt`) relança `CancellationException`; troca em 13 `*RepositoryImpl`; regra `repositorio nao usa runCatching que engole cancelamento` + teste do padrão; `CLAUDE.md` e `docs/integrations.md` | Vermelho: `MiniMaxRepositoryImplTest.kt:91` → `Expected an exception of class CancellationException to be thrown, but was completed successfully with the result: <Failure(CancellationException: cancelado pelo escopo)>`; `SuspendRunCatchingTest.kt:29` (stub) idem; `ArchitectureRulesTest.kt:300` → `runCatching em repositório engole CancellationException` + 13 arquivos. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.architecture.*"` → 709 testes, 0 falhas. `grep 'runCatching' data/repository/*RepositoryImpl.kt` → vazio |

## Problemas em aberto e riscos

- A03 é mecânica e extensa: o risco é anotar com nome diferente do atual e mudar o JSON. A regra nova só verifica a
  presença da anotação; a igualdade com o nome atual é verificada pelos testes de serialização existentes e pelo diff.
- A05 troca a raiz de 7 telas: a cor de conteúdo herdada (`LocalContentColor`) precisa continuar igual.
