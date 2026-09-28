# /release-beta — Beta Release Workflow

Publica uma beta (issue #355): tag anotada `vX.Y.Z-beta.N`; o CI gera os pacotes e publica o GitHub
Release como **prerelease**, nunca como `latest`. Só quem ligou "Receber versões beta" nas
Configurações recebe.

> Fluxo mecânico — sem Plan Mode, sem subagentes. Fonte completa: `.claude/skills/usage-monitor-release-beta/SKILL.md`.

## Usage

```
/release-beta minor   # nova série: próxima estável minor, beta.1
/release-beta next    # continua a série aberta: beta.N+1
```

## Steps

1. `git fetch origin --tags && git status --short --branch` — `main` limpo e igual a `origin/main`.
2. Última estável: `git describe --tags --abbrev=0 --match "v[0-9]*" --exclude "*-beta*"`.
   Série aberta: `git -c versionsort.suffix=- tag --sort=-version:refname --list "vX.Y.Z-beta.*" | head -n 1`.
   Nunca taguear beta de um `X.Y.Z` já lançado como estável.
3. `gradlew.bat allTests` (obrigatório). Host de janela mudou desde a última tag? Perguntar pelo teste
   no Linux (X11) antes da tag (#342).
4. `git -c user.name=claude -c user.email=claude@anthropic.com tag -a vX.Y.Z-beta.N -m "vX.Y.Z-beta.N"`
   e `git push origin vX.Y.Z-beta.N` (só a tag).
5. Acompanhar `Release Desktop Packages`; conferir `gh release view vX.Y.Z-beta.N --json isPrerelease`
   → `true`, `/releases/latest` ainda na última estável, e todos os artefatos com digest `sha256:`.
