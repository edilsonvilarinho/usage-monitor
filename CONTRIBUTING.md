# Contributing

Thanks for taking the time. This guide covers the build, the test suite and the conventions this
repository enforces. For how the code is organised, read
[`docs/architecture.md`](docs/architecture.md) first.

## Prerequisites

- **JDK 17.** The build pins `jvmToolchain(17)`.
- Nothing else. The Gradle wrapper brings its own Gradle, and Compose Desktop brings its own runtime.

NSIS is only needed if you want to build the Windows installer locally.

## Running and building

```bat
gradlew.bat run                 :: run the desktop app
gradlew.bat desktopJar          :: executable JAR
gradlew.bat createDistributable :: desktop image
gradlew.bat packageInstaller    :: NSIS installer (Windows, needs NSIS)
gradlew.bat clean
```

Packaging, CI and the auto-update mechanism are documented in
[`docs/build-and-release.md`](docs/build-and-release.md).

## Tests

**There is no root `test` task** in this KMP project. The aggregate gate is `allTests`.

```bat
gradlew.bat allTests                                        :: everything
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"     :: Compose component tests
```

- `commonTest` covers domain, mappers, history, forecasting and ViewModels.
- `desktopTest` covers SQLite data sources, Compose Desktop components and the desktop update flow.

Parallel forks (`-PtestForks=4`) are opt-in and only safe on a machine with a warm `~/.skiko`. See
[`docs/build-and-release.md`](docs/build-and-release.md#parallel-test-forks) for why.

### Two traps that cost a red suite

- **`delay` inside `runTest` advances virtual time and does not wait for background work.** The
  ViewModels run on `Dispatchers.Default`, so a wait written with `delay` returns immediately. Wait
  for ViewModel state with `yield()` + `Thread.sleep`, as `pauseForBackgroundWork` does in
  `DashboardViewModelTestSupport`.
- **No new infinite animations.** They hang `waitForIdle` in the component tests. Progress is text,
  not a spinner.

## Code conventions

- **Identifiers in English, comments in Portuguese.** This is the existing convention throughout the
  codebase; keep it consistent rather than mixing styles within a file.
- Avoid nested scope functions (`let`, `apply`, `run`). Prefer explicit flow.
- UI components are **stateless**: data in through parameters, events out through lambdas.
  `DashboardScreen` is the single stateful screen.
- Preserve the partial-success behaviour of `UiState`: when one source fails and another succeeds,
  the UI stays in `Success` with the errors listed.
- **Never hardcode a secret**, and never read an API key from an environment variable.
- **No new value in an existing enum** unless you have a reason worth writing down. The exhaustive
  `when` blocks are the compiler-enforced checklist that a new case was handled everywhere; adding a
  field with a default is backward-compatible, adding an enum value is not.

## Visual changes

The visual source of truth is [`docs/design-system/`](docs/design-system/readme.md), and the approved
prototype in [`docs/planos/prototipo-visual-opencode.html`](docs/planos/prototipo-visual-opencode.html)
is the mandatory mockup for each screen's layout.

- **No screen reimplements a primitive.** Before writing a `Surface`, a `Modifier.border`, a
  `.background` with a surface colour, or a `RoundedCornerShape`, look in
  `presentation/ui/components/`. If the primitive does not exist, the commit that creates it and the
  commit that consumes it are the same commit.
- Accent colours come from `AppAccents.current` and `AppTone` — never from `darkAppAccents` or
  `lightAppAccents` directly. A top-level `val` is resolved once per process and does not read the
  active theme.
- **Colour never carries information on its own.** Every state carries a dot and a word.
- If you change a screen, **regenerate the screenshots** and commit them:

```bat
gradlew.bat generateScreenshots
gradlew.bat generateTourGif
```

## Commits and pull requests

- **Conventional Commits, in English**: `feat(update): …`, `fix(team): …`, `docs: …`.
- **One activity, one commit.** Code, test and documentation for the same decision go in together. A
  commit that only compiles alongside the next one is not atomic, and a commit that bundles two
  decisions cannot be reverted without losing one of them.
- Open the pull request against `main`. CI runs `allTests` on Windows; a red suite blocks the merge
  and blocks releases.
- If your change alters behaviour a user can see, update the README **and** `README.pt-BR.md`. The
  English one is canonical.

Work that follows a written plan keeps its status table in `docs/planos/`, one row per activity,
written in the same commit as the activity it describes.

## Reporting problems

Bugs and feature requests go through
[GitHub Issues](https://github.com/edilsonvilarinho/usage-monitor/issues). Vulnerabilities do not —
see [`SECURITY.md`](SECURITY.md).
