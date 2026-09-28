---
name: usage-monitor-release-beta
description: Cut and publish a beta (prerelease) of the usage-monitor app — an annotated vX.Y.Z-beta.N tag that the release workflow publishes as a GitHub prerelease, offered only to users who turned on "Receber versões beta" in Settings. Use when Claude needs to ship, publish, or troubleshoot a beta release for this project; for a stable release use usage-monitor-release.
---

# Usage Monitor Beta Release

Publish a beta for the users who opted into the beta channel (issue #355). It is the stable release
flow ([usage-monitor-release](../usage-monitor-release/SKILL.md)) with a different tag and a
different promise: **a beta never becomes `latest`**, so nobody outside the channel receives it.

## How the channel works (read before tagging)

- Tag format: `vX.Y.Z-beta.N`, `N ≥ 1`. Nothing else is accepted — `verify-version` rejects
  `-rc.1`, `-beta` without a number and any other suffix, and the Linux updater refuses them too.
- The workflow sets `prerelease: true` and `make_latest: false` for `-beta.N` tags. Clients outside
  the channel read `/releases/latest`, which the GitHub API never answers with a prerelease — that is
  the whole protection, and it also covers app versions older than the channel.
- Clients inside the channel list `/releases?per_page=20` and take the highest non-draft version by
  SemVer precedence: `42.0.0-beta.1 < 42.0.0-beta.2 < 42.0.0`. The stable `42.0.0` therefore reaches
  every beta tester of the `42.0.0` series. Turning the channel off never downgrades.
- `X.Y.Z` is the **next stable** version the beta previews. Never tag a beta for a number that is
  already released as stable: `41.0.0-beta.1` after `v41.0.0` is lower than the running stable and is
  never offered to anyone.
- Packaging: Exe/Dmg carry only `X.Y.Z` (jpackage rejects the suffix), Deb/Rpm carry `X.Y.Z~beta.N`;
  `CURRENT_APP_VERSION`, the NSIS installer name, the tarball and the release assets keep the full
  `X.Y.Z-beta.N`.

## Workflow

1. Read [AGENTS.md](../../../AGENTS.md), [build.gradle.kts](../../../build.gradle.kts) and
   [.github/workflows/release-linux.yml](../../../.github/workflows/release-linux.yml) first.
2. Invoking this skill already means "cut and publish the beta". Do not ask for confirmation to tag or
   push — go through the whole flow and report the result. The only question allowed is the one in
   step 6 (window hosts).
3. Pick the beta series from the request:
   - `patch` / `minor` / `major` — start a new series for the next stable of that type:
     `N = 1`.
   - `next` (or no type while a series is open) — continue the open series: `N + 1`.
   - If the request is ambiguous and there is no open series, ask which type.
4. Verify that `main` is clean and matches `origin/main` — the tag must point to a commit already on
   the remote `main`, or `verify-version` refuses it:
   - `git fetch origin --tags && git status --short --branch`
   - `git log --oneline -5`
5. Compute the version. **There is no version bump commit** — the version comes from the tag.
   - Last stable: `git describe --tags --abbrev=0 --match "v[0-9]*" --exclude "*-beta*"`.
   - Betas of the series: `git tag --list "vX.Y.Z-beta.*"`, highest `N` by numeric sort
     (`git -c versionsort.suffix=- tag --sort=-version:refname --list "vX.Y.Z-beta.*" | head -n 1`).
   - New series: `X.Y.Z` = last stable bumped by the type; refuse if `vX.Y.Z` already exists.
   - Continue: same `X.Y.Z`, `N + 1`; refuse if the stable `vX.Y.Z` already exists (the series is
     closed — start a new one).
6. Run the local verification that must pass before the tag exists:
   - `gradlew.bat allTests` — mandatory. A beta is still a public tag; a broken suite discovered after
     the push is a burned tag.
   - **Window hosts changed? Stop before the tag** (#342), exactly as in the stable skill: run
     `git diff --name-only $(git describe --tags --abbrev=0)..HEAD` and look for `HudWindow.kt`,
     `DesktopWindowFrame.kt`, `Main.kt` or any `*WindowHost*.kt`. If one changed, list the rows of
     [`docs/hud-notch.md` › "Fora do alcance dos testes"](../../../docs/hud-notch.md#fora-do-alcance-dos-testes)
     the change touches and ask the maintainer whether the build was opened on Linux (X11). A beta is
     a good place to *find* such bugs, but the testers still run it as their daily app.
   - Do not package locally as a gate; CI owns packaging for the three OSes.
7. Publish:
   - annotated tag with the temporary agent git identity:
     `git -c user.name=claude -c user.email=claude@anthropic.com tag -a vX.Y.Z-beta.N -m "vX.Y.Z-beta.N"`
   - push **only the tag**: `git push origin vX.Y.Z-beta.N`.
8. Watch `Release Desktop Packages` and report the outcome. Then confirm on the published release:
   - it is marked **Pre-release**:
     `gh release view vX.Y.Z-beta.N --json isPrerelease` → `true` (`gh` has no `isLatest` field);
   - it is **not** Latest — `/releases/latest` still points to the last stable:
     `gh api repos/edilsonvilarinho/usage-monitor/releases/latest --jq .tag_name`;
   - every artifact family is there, each with a `sha256:` digest (auto-update refuses assets without it):
     `UsageMonitor-Setup-X.Y.Z-beta.N.exe`; `.deb`, `.rpm` and `usage-monitor_X.Y.Z-beta.N_linux_x64.tar.gz`;
     `usage-monitor_X.Y.Z-beta.N_macos_arm64.dmg` and `usage-monitor_X.Y.Z-beta.N_macos_x64.dmg`.
     `gh release view vX.Y.Z-beta.N --json assets --jq '.assets[] | [.name, .digest] | @tsv'`
   A beta missing an artifact family, or published as Latest, is a failed release and must be reported
   as such. If it went out as Latest, edit it back with
   `gh release edit vX.Y.Z-beta.N --prerelease --latest=false` and report it — every user outside the
   channel was being offered the beta.
9. The release notes come from the workflow's `Generate release notes` step: a beta diffs against the
   previous tag (beta or stable); the stable release later diffs against the previous **stable**, so it
   lists the whole series. Do not write them by hand.

## Guardrails

- Never publish a stable version from this skill; `vX.Y.Z` tags belong to `usage-monitor-release`.
- Never tag a suffix other than `-beta.N`.
- Never tag a beta whose `X.Y.Z` is already released as stable.
- Do not release from a dirty tree unless the user explicitly wants that risk.
- Do not ask the user to confirm the tag or the push; the one exception is step 6 (#342).
- Prefer current code and workflow files over older notes when they disagree.
