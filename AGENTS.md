# AGENTS.md

## Build commands (Windows)

All commands use `gradlew.bat` (not `./gradlew`):
```bat
gradlew.bat run           % run desktop app
gradlew.bat desktopJar    % build JAR without running
gradlew.bat test          % all tests (domain + data + ViewModel + UI)
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"   % unit tests only
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"       % Compose component tests only
gradlew.bat clean         % clear build cache
```

**Required env var before running:**
```bat
set MINIMAX_API_KEY=your_key_here
```

## Architecture

Three layers with unidirectional deps: `presentation → domain ← data`

| Source set | Platform | Contents |
|---|---|---|
| `commonMain` | shared | domain + data (DTOs, mappers, repos) + presentation/UI |
| `desktopMain` | JVM only | `LocalCredentialDataSource` (java.io.File) + `Main.kt` (DI) |
| `commonTest` | shared | unit tests (domain, mappers, ViewModel) |
| `desktopTest` | JVM only | Compose component tests |

## Domain layer constraints

- **Zero imports** of Ktor, Compose, or any external library. Only pure Kotlin + kotlinx.datetime.
- `AnthropicRateLimitDto` is **not parsed from JSON** — populated manually from HTTP headers.
- `LocalCredentialDataSource` lives in **desktopMain**, not commonMain — it uses `java.io.File`.

## DI and lifecycle

`Main.kt` builds the graph manually: `HttpClient(OkHttp)` → `RemoteApiDataSource` → repos → use cases → `DashboardViewModel` → `DashboardScreen`.

`DashboardViewModel` polls every 10 min via `while(true) + delay`. Uses `SupervisorJob` scope so one failing API doesn't cancel the others. **Must call `viewModel.onDestroy()` on window close.**

## External API calls

- Anthropic: `POST /v1/messages` with minimal payload (claude-haiku, max_tokens=1) — only to read rate-limit headers. Reads token from `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`.
- MiniMax: `GET https://www.minimax.io/v1/token_plan/remains` — reads `MINIMAX_API_KEY` from env, **never hardcoded**.

## Code style

- Names in English, comments in Portuguese.
- Avoid nested scope functions (`let`, `apply`, `run`). Prefer explicit flow.
- UI components: **stateless** (data via params, events via lambdas). `DashboardScreen` is the only stateful component.

## Commit convention

Commits in my name and email: **minimax <minimax@opencode.ai>**.

**Never run `git commit` or `git push` unless the user explicitly asks.**

## NSIS Installer - Debugging Lessons

**Problema**: Installer travava em 99% e na tela de finish

**Causas raiz**:
1. **LZMA compression** → thread deadlock (NSIS Bug #248)
2. **ExecWait** → bloqueia installer esperando app fechar

**Soluções**:
1. `SetCompressor zlib` no topo do .nsi (evita deadlock LZMA)
2. `Exec` ao invés de `ExecWait` em `.onInstSuccess` (não bloqueia)

**Sintomas**:
- 99% freeze = problema de compressão LZMA
- Tela de finish freeze = ExecWait bloqueando

**Diagnóstico correto**:
- Analisar imagens com atenção (freeze era NO FINAL, não na extração)
- Pesquisar ANTES de chutar soluções
- Perguntar "onde exatamente trava?" antes de propor fixes