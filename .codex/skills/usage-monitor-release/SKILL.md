---
name: usage-monitor-release
description: Prepare and publish a versioned release for the usage-monitor repository, including version bump, local verification, git tag creation, and the GitHub Actions workflow that publishes Windows and Linux artifacts. Use when Codex needs to cut, ship, publish, or troubleshoot a release for this project.
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
   - The official GitHub release artifacts come from the tag-driven workflow in [.github/workflows/release-linux.yml](../../../.github/workflows/release-linux.yml).
7. Run the narrowest verification that still proves the release is sound:
   - Shared code changes: `gradlew.bat allTests`
   - Packaging smoke check: `gradlew.bat createDistributable packageDistributionForCurrentOS`
   - Windows installer check when requested: `gradlew.bat packageInstaller`
8. If the user also asked to publish:
   - commit the version bump with the temporary `codex` git identity
   - create the annotated tag `vX.Y.Z`
   - push `main` and the tag
9. Watch the GitHub Actions workflow `Release Desktop Packages` and report the outcome.

## Guardrails

- Do not release from a dirty tree unless the user explicitly wants that risk.
- Do not create a tag or push without explicit permission.
- Do not forget that release artifacts are published by CI from `v*` tags.
- Prefer current code and workflow files over older notes in `.agents`, `.claude`, or `docs/research.md` when they disagree.
