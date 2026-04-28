# /release — Release Workflow

Cria uma versão: bump, build, tag e GitHub Release com assets.

> Fluxo mecânico — sem Plan Mode, sem subagentes.

## Usage

```
/release patch   # 1.0.0 → 1.0.1  (bug fixes)
/release minor   # 1.0.0 → 1.1.0  (new features)
/release major   # 1.0.0 → 2.0.0  (breaking changes)
```

Se não informado, perguntar o tipo antes de prosseguir.

---

## Step 1 — Confirm current state

```bash
git status && git log --oneline -5
```

`main` deve estar limpo e atualizado com origin. Se não, parar e informar.

## Step 2 — Bump version

**build.gradle.kts** (line ~90):
```
packageVersion = "X.Y.Z"
```

**src/installer/UsageMonitor.nsi** (line ~11):
```
!define PRODUCT_VERSION "X.Y.Z"
```

Calcular nova versão baseada no tipo (patch/minor/major) e atualizar ambos arquivos com Edit tool.

## Step 3 — Commit

```bash
git add build.gradle.kts src/installer/UsageMonitor.nsi
git commit -m "chore: bump version to v<X.Y.Z>"
```

## Step 4 — Build

```bash
powershell -ExecutionPolicy Bypass -File build-with-icon.ps1
```

Se falhar, parar — não criar tag nem release com build quebrado. Verificar que `build/installer/UsageMonitor-Setup-<X.Y.Z>.exe` existe (~60 MB).

## Step 5 — Create annotated git tag

```bash
git tag -a v<X.Y.Z> -m "v<X.Y.Z>"
```

## Step 6 — Push commit and tag

```bash
git push origin main && git push origin v<X.Y.Z>
```

## Step 7 — Collect changelog

```bash
git log --oneline <previous-tag>..HEAD
```

Agrupar por tipo: `feat`, `fix`, `chore`.

## Step 8 — Create GitHub Release with assets

```bash
gh release create v<X.Y.Z> \
  "build/installer/UsageMonitor-Setup-<X.Y.Z>.exe" \
  --title "v<X.Y.Z>" \
  --notes "## What's Changed\n\n### Features\n- ...\n\n### Bug Fixes\n- ...\n\n**Full changelog:** https://github.com/edilsonvilarinho/usage-monitor/compare/v<prev>...v<X.Y.Z>"
```

Compartilhar a URL do GitHub Release.