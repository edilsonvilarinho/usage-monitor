---
name: usage-monitor-release
description: Prepare and publish a versioned release for the usage-monitor repository, including version bump, local verification, git tag creation, and the GitHub Actions workflow that publishes Windows, Linux, and macOS artifacts. Use when Codex needs to cut, ship, publish, or troubleshoot a release for this project.
---

# Usage Monitor Release

Release this repository in a way that stays aligned with the current build, installer, and GitHub Actions workflow.

## Workflow

1. Read [AGENTS.md](../../../AGENTS.md), [README.md](../../../README.md), [build.gradle.kts](../../../build.gradle.kts), and [.github/workflows/release-linux.yml](../../../.github/workflows/release-linux.yml) first.
2. Continue only after the user explicitly asks to create or publish a release.
3. Confirm the release type from the request:
   - `patch`
   - `minor`
   - `major`
4. Verify that `main` is clean and current enough for a release:
   - `git status --short --branch`
   - `git log --oneline -5`
5. Bump `version = "X.Y.Z"` in [build.gradle.kts](../../../build.gradle.kts).
6. Keep release-related packaging aligned:
   - The Gradle `buildNsisInstaller` task passes `/DPRODUCT_VERSION=$appVersion` to NSIS.
   - The local helper [build-with-icon.ps1](../../../build-with-icon.ps1) is Windows-only and exists for icon-patched local packaging.
   - The official GitHub release artifacts come from the tag-driven workflow in [.github/workflows/release-linux.yml](../../../.github/workflows/release-linux.yml), which builds Linux, Windows, and macOS.
   - macOS `.dmg` files come from the `build-macos` job (`macos-latest` for arm64, `macos-15-intel` for x64). They are unsigned: no Apple Developer ID, no notarization. `packageDmg` cannot run on Windows or Linux, so there is no local validation path for it.
7. Run the narrowest verification that still proves the release is sound:
   - Shared code changes: `gradlew.bat allTests`
   - Packaging smoke check: `gradlew.bat createDistributable packageDistributionForCurrentOS` — this only covers the local OS; macOS coverage comes from CI.
   - Windows installer check when requested: `gradlew.bat packageInstaller`
8. If the user also asked to publish:
   - commit the version bump with the temporary `codex` git identity
   - create the annotated tag `vX.Y.Z`
   - push `main` and the tag
9. Watch the GitHub Actions workflow `Release Desktop Packages` and report the outcome. The published release must carry every artifact family:
   - Windows: `Usage Monitor-X.Y.Z.msi` and `UsageMonitor-Setup-X.Y.Z.exe`
   - Linux: `.deb`, `.rpm`, and `usage-monitor_X.Y.Z_linux_x64.tar.gz`
   - macOS: `usage-monitor_X.Y.Z_macos_arm64.dmg` and `usage-monitor_X.Y.Z_macos_x64.dmg`
   A release missing either DMG is incomplete and must be reported as such.
10. The published GitHub Release should include a short changelog built from commits between the previous `v*` tag and the new tag.

## Guardrails

- Do not release from a dirty tree unless the user explicitly wants that risk.
- Do not create a tag or push without explicit permission.
- Do not forget that release artifacts are published by CI from `v*` tags.
- Do not forget that the GitHub Release body should show the commit summary for the new version.
- Do not treat a macOS-only job failure as acceptable: the `build-macos` matrix blocks `publish-release`, and a green release without both DMGs means the workflow was edited or skipped.
- Prefer current code and workflow files over older notes in `.agents`, `.claude`, or `docs/research.md` when they disagree.
