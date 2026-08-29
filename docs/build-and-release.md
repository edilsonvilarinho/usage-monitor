# Build and release

## Local build

| Command | Result |
|---|---|
| `gradlew.bat run` | runs the desktop app |
| `gradlew.bat desktopJar` | executable JAR |
| `gradlew.bat createDistributable` | desktop image in `build/compose/binaries/main/app/Usage Monitor` |
| `gradlew.bat packageInstaller` | NSIS installer, when NSIS is installed |
| `gradlew.bat packageDmg` | macOS only — jpackage does not cross-compile |
| `gradlew.bat generateScreenshots` | regenerates `img/*.png` |
| `gradlew.bat generateTourGif` | regenerates `img/tour.gif` |

`build-with-icon.ps1` is a Windows helper flow: it builds the distributable, applies the icon with
`rcedit`, and calls NSIS by hand.

The app version comes from `build.gradle.kts` and is propagated into the generated
`CURRENT_APP_VERSION` constant. It is never written by hand.

## Distribution formats

`TargetFormat.Exe` (Windows), `Deb` and `Rpm` (Linux), `Dmg` (macOS).

**`Msi` was dropped.** Both Windows installers wrote to the same `%LOCALAPPDATA%\Usage Monitor`, and
the MSI one could never update itself — the auto-updater only accepts the NSIS artifact. The
`upgradeUuid` stays in `build.gradle.kts` because it is the UpgradeCode of the MSI installations that
already exist, and it is how `UsageMonitor.nsi` finds and removes them before installing.

The DMGs ship **without an Apple Developer ID signature**, so Gatekeeper needs a manual override on
first launch.

## CI

| Workflow | Runs |
|---|---|
| `.github/workflows/ci.yml` | `allTests` on Windows, on push to `main` and on pull request |
| `.github/workflows/ci-server.yml` | `typecheck` and `vitest` for `server/`, when `server/**` changes |
| `.github/workflows/release-linux.yml` | on `v*` tags: publishes Windows, Linux and macOS artifacts |

Both test jobs publish a summary to `$GITHUB_STEP_SUMMARY` — test counts and slowest classes when
they run, and an explicit **not executed** notice with the reason when the path filter skips them. A
job that passes in five seconds without running a single test is otherwise indistinguishable from one
that ran the suite.

The release job `verify` runs `allTests` in parallel with the builds, and `publish-release` depends
on it: a red suite does not publish a release.

### Gradle cache

The cache comes from `gradle/actions/setup-gradle`, **not** from `setup-java`'s `cache: 'gradle'`.
The latter archives `~/.gradle` with the daemon still alive, and on Windows `tar` dies on the `.lock`
files. Only `main` writes the cache: a cache written from a pull request run is scoped to that PR and
no other run can read it.

### Parallel test forks

The suite runs in a single fork. `-PtestForks=N` is opt-in and must only be used on a machine with a
warm `~/.skiko`: Skiko unpacks `skiko-windows-x64.dll` with a `Files.move` that fails with
`AccessDeniedException` when another process has already opened the destination. On a clean runner
every fork tries to unpack at once.

**If UI tests are green locally and red in CI, look at `~/.skiko` before you look at the test.**

### Coverage

Kover instrumentation is opt-in via `-Pcoverage`, because it costs 6–7 s per run. Only the push to
`main` turns it on.

```bat
gradlew.bat allTests -Pcoverage
gradlew.bat koverHtmlReport -Pcoverage
```

There is no `koverVerify` and no floor: a threshold calibrated before the baseline existed is a
threshold calibrated in the dark. Baseline as of 2026-08-25: **82.7% of lines**, 52.3% of branches.

## NSIS installer

Lessons already paid for in this project:

- Use `SetCompressor zlib` at the top of the `.nsi`. A freeze at 99% usually means an LZMA
  compression problem.
- Avoid a blocking `ExecWait` launch in the success path. A freeze on the final screen usually means
  a blocking process.

The installer is per-user with `RequestExecutionLevel user` — no UAC prompt. That is what makes the
silent auto-update swap viable: it extracts to `$INSTDIR.new` and only swaps with two `Rename` calls
on the same volume once the new tree is complete. A successful `Rename` **is** the proof that the
previous process exited; `taskkill /F` is not, and could kill it mid-SQLite-write.

## Auto-update

Off by default. Enabled, it downloads the release in the background, validates the SHA-256 against
the `digest` field of the GitHub API — not the hash published by the workflow, which only serves the
initial installer — and swaps on app exit, or immediately via **Restart and update now**.

| Platform | Supported install | Notes |
|---|---|---|
| Windows | NSIS per-user (`UsageMonitor-Setup-*.exe`) | MSI and manual copies are excluded |
| Linux x64 | user-space `.sh` install in a managed XDG tree | `.deb`/`.rpm` belong to the package manager |
| Linux ARM64 | — | no ARM64 tarball is published |
| macOS | — | no Developer ID, no reliable way to remount the bundle under quarantine |

Each installer sits behind its own build flag and a **minimum target version**: below that floor, the
installed version does not understand the confirmation handshake and the updater would roll back an
update that actually worked.

On Linux, `current` is a **text file holding the version, not a symlink** — `mv -T` is not POSIX. The
script promotes by plain `rename(2)`, relaunches the stable launcher, and waits for a file-based ACK
carrying a token the script generated. Without the ACK in 60 s it rolls back. The log always lands in
`~/.usage-monitor/diagnostics/linux-update.log`.

Progress is reported as **text** ("Downloading 42%"), never an infinite animation — those hang
`waitForIdle` in the component tests.

## Branding

`tools/brand/render_icons.py` generates the PNG, ICO and ICNS from a monogram described in code.
The `monogram.svg` beside it is reference material and is not read by the script. The `.icns` is only
validated in the `build-macos` release job.
