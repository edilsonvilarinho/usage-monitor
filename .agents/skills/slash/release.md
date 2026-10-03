# /release — Release Workflow

Cria uma versão: tag anotada; o CI gera os pacotes e o GitHub Release.

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

## Step 2 — Pick the version

Sem commit de bump (#344): a versão vem da tag.

```bash
git describe --tags --abbrev=0 --match "v[0-9]*" --exclude "*-beta*"
```

Tags beta (`vX.Y.Z-beta.N`, issue #355) ficam de fora: beta tem skill própria
(`usage-monitor-release-beta`) e nunca é a base da estável.

Calcular a próxima versão pelo tipo (patch/minor/major). Não editar `build.gradle.kts` nem
`src/installer/UsageMonitor.nsi`: o release passa `-PappVersion` a partir da tag.

## Step 3 — Verify locally

```bash
gradlew.bat allTests
```

Se falhar, parar — a tag é o que dispara o release.

## Step 4 — Create annotated tag and push only the tag

```bash
git tag -a v<X.Y.Z> -m "v<X.Y.Z>"
git push origin v<X.Y.Z>
```

A tag precisa apontar para um commit que já está na `main` remota e ser anotada, ou o job
`verify-version` recusa.

## Step 5 — Watch the release workflow

O workflow `Release Desktop Packages` gera os pacotes de Windows, Linux e macOS, roda o `verify` e
publica o GitHub Release com as notas geradas dos commits. Não usar `gh release create` à mão.

```bash
gh run watch $(gh run list --workflow=release-linux.yml --limit 1 --json databaseId -q '.[0].databaseId')
```

Compartilhar a URL do GitHub Release.
