# Architecture

Kotlin Multiplatform with a single JVM (desktop) target. Three layers, dependencies in one
direction:

```
presentation → domain ← data
```

`domain` is the pure core: it may not import Compose, Ktor, or any infrastructure library. Entities
and contracts know nothing about HTTP, JSON, local files, or UI.

## Source sets

| Source set | Role |
|---|---|
| `commonMain` | domain, data, presentation and shared UI |
| `desktopMain` | desktop bootstrap, local data sources, SQLite, auto-start, update installers |
| `commonTest` | domain, mappers, history and ViewModel tests |
| `desktopTest` | Compose Desktop component tests and JVM data source tests |
| `src/installer` | NSIS installer scripts, languages and assets |

Data sources that read the user's files live in `desktopMain`, because they use `java.io.File`.

## Layer rules

- **domain** — entities with computed properties, repository interfaces, and use cases exposed as
  `operator fun invoke()`. No external dependency.
- **data** — DTOs with `@Serializable`/`@SerialName`, repository implementations, mappers. Failures
  are wrapped with `Result.runCatching { }`.
- **presentation** — `UiState` is a sealed interface with `Loading`, `Success(data)` and
  `Error(message)`. If one API fails while another succeeds, it emits `Success` with partial data;
  that behaviour must be preserved.

UI components are **stateless**: data comes in through parameters, events go out through lambdas.
`DashboardScreen` is the single stateful screen.

`DashboardViewModel` polls silently every 600 seconds on a scope with a `SupervisorJob`, so one
failing coroutine does not cancel the others. `viewModel.onDestroy()` must be called when the window
closes.

## Bootstrap and dependency injection

Manual, no framework, in `src/desktopMain/kotlin/com/usagemonitor/Main.kt`:

```
HttpClient(OkHttp) → data sources → repositories → use cases → ViewModels → Compose screens
```

`Main.kt` closes `DashboardViewModel`, `HistoryViewModel` and the `HttpClient` both on window close
and in the shutdown hook.

> **`main()` is at the limit of the JVM backend.** It is a single composable over a thousand lines
> long, and control-flow analysis over the whole method has already blown up with `OutOfMemoryError`
> inside ASM. Extract into smaller composables before adding more state there.

Main graph components:

| Kind | Classes |
|---|---|
| Local data sources | `LocalCredentialDataSource`, `LocalCodexAuthDataSource`, `LocalOpenCodeUsageDataSource`, `LocalKiloUsageDataSource`, `LocalUsageHistoryDataSource`, `LocalApiKeyDataSource`, `LocalTeamSettingsDataSource` |
| Remote data sources | `RemoteApiDataSource`, `RemoteTeamDataSource` |
| Repositories | `AnthropicRepositoryImpl`, `CodexRepositoryImpl`, `MiniMaxRepositoryImpl`, `DeepSeekRepositoryImpl`, `OpenCodeRepositoryImpl`, `OpenCodeGoRepositoryImpl`, `KiloRepositoryImpl`, `UsageHistoryRepositoryImpl`, `AppUpdateRepositoryImpl` |
| Installers | `WindowsAppUpdateInstaller`, `LinuxAppUpdateInstaller` |

## Where things are stored

| Path | Content |
|---|---|
| `~/.usage-monitor/usage-history.db` | SQLite: usage history, Claude Code session index, team sync state |
| `~/.usage-monitor/api-keys.json` | API keys — atomic write, owner-only permissions |
| `~/.usage-monitor/team.json` | Team server URL and key — a secret, deliberately kept out of the preference store |
| `~/.usage-monitor/diagnostics/` | Startup log, and the opt-in recorders |
| `Preferences.userRoot().node("com.usagemonitor")` | UI preferences: enabled APIs, theme, language, auto-start, card order, window geometry, UI scale, opacity, alert thresholds |

Preferences are written in the clear (the Windows registry, for instance), which is why the team key
and the API keys do not live there.

## Notable files

| File | Role |
|---|---|
| `src/desktopMain/kotlin/com/usagemonitor/Main.kt` | bootstrap, preferences, main composition |
| `src/commonMain/kotlin/com/usagemonitor/presentation/viewmodel/DashboardViewModel.kt` | polling, refresh, snapshots, update flow |
| `src/commonMain/kotlin/com/usagemonitor/data/repository/UsageHistoryRepositoryImpl.kt` | history aggregation and forecast |
| `src/commonMain/kotlin/com/usagemonitor/data/datasource/RemoteApiDataSource.kt` | remote HTTP calls |
| `src/desktopMain/kotlin/com/usagemonitor/data/datasource/AnthropicCredentialStore.kt` | Anthropic credential origin (file, or macOS Keychain) |
| `src/desktopMain/kotlin/com/usagemonitor/data/datasource/LocalObservedModelUsageReader.kt` | reads the local OpenCode and Kilo databases |
| `src/installer/UsageMonitor.nsi` | NSIS installer script |

## Design system

The visual source of truth is [`docs/design-system/`](design-system/readme.md): tokens, published
primitives with their written contracts, and the content and iconography rules. The approved
prototype, [`docs/planos/prototipo-visual-opencode.html`](planos/prototipo-visual-opencode.html), is
the mandatory mockup for every screen's layout.

No screen reimplements a primitive: before writing a `Surface`, a `Modifier.border`, or a
`RoundedCornerShape`, look in `presentation/ui/components/`.

Both documents are in Portuguese and are internal working documents.

## Team server

The optional team integration is a self-hosted Node.js server. Its API contract, deployment guide and
security model live in [`server/README.md`](../server/README.md).
