---
name: usage-monitor-commit-push
description: Safely stage, commit, and push intended changes in the usage-monitor repository with the repo's required temporary git identity, narrow verification, and clean restoration of the original author config. Use when Codex needs to commit or push local changes for this project, especially when unrelated files may exist or the user explicitly asks to publish work.
---

# Usage Monitor Commit Push

Commit only the intended files, use the repository's temporary `codex` git identity, and restore the original local git author config before finishing.

## Workflow

1. Read [AGENTS.md](../../../AGENTS.md) and [README.md](../../../README.md) first.
2. Continue only after the user explicitly asks to commit, push, or publish changes.
3. Inspect repository state with:
   - `git status --short --branch`
   - `git branch --show-current`
   - `git remote -v`
   - `git diff --stat`
   - `git diff --cached --stat`
4. Stage only explicit file paths. Never use broad staging such as `git add .` when unrelated changes may exist.
5. Choose the narrowest relevant verification before the commit:
   - Docs-only changes: inspect the diff for coherence.
   - Kotlin, UI, history, lifecycle, or repository changes: run `gradlew.bat allTests` or a narrower relevant test target.
   - Installer or packaging changes: run `gradlew.bat packageInstaller` when the installer behavior is part of the change.
6. Prefer the bundled script `scripts/commit_and_push.ps1` for the actual commit and push.
7. Report the branch, commit hash, push result, and final `git status --short --branch`.

## Script

Use the bundled PowerShell helper to stage only the requested files, temporarily switch git identity to `codex`, restore the previous identity, and optionally skip the push.

Example:

```powershell
powershell -ExecutionPolicy Bypass -File `
  .codex\skills\usage-monitor-commit-push\scripts\commit_and_push.ps1 `
  -RepoPath C:\Users\edils\workspace\usage-monitor `
  -Message "docs: sync README with current architecture and workflows" `
  -Files README.md `
  -TempUserName codex `
  -TempUserEmail codex@openai.com
```

Use `-SkipPush` when the user asked only for a local commit.

## Guardrails

- Do not commit or push without explicit user approval in the current conversation.
- Do not stage unrelated changes.
- Do not force-push unless the user explicitly asks for it.
- Keep commit messages focused on the actual change scope.
