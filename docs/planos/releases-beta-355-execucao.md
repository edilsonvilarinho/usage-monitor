# Releases beta opt-in (issue #355) — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (desktop) |
| **Data** | 2026-09-28 |
| **Branch** | `feat/beta-releases-355`, criada de `main` (`7a7655d`) |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

## Contexto

Queremos testar mudanças com um grupo pequeno de usuários antes da release estável. Para isso:

1. Configurações → Geral → Sistema ganha o toggle **"Receber versões beta"**, desligado por padrão.
2. A skill `usage-monitor-release-beta`, derivada de `usage-monitor-release`, publica
   `vX.Y.Z-beta.N` como **prerelease** no GitHub.
3. Quando a atualização oferecida, as novidades ou a versão em uso são beta, o app diz **beta** nas
   mesmas superfícies da release normal: banner de atualização, balão da HUD, janela de novidades e
   rodapé.

Antes deste trabalho, tudo assumia versão só numérica: `AppVersionComparison.kt` descartava o
sufixo (`beta.1 == beta.2 == estável`), o `verify-version` do `release-linux.yml` rejeitava a tag, o
updater do Linux abortava com `invalid-version` e a nota de release ordenava a beta depois da
estável.

## Decisões travadas

| Pergunta | Escolha |
|---|---|
| Formato da tag | `vX.Y.Z-beta.N` (N ≥ 1), anotada, commit ancestral de `origin/main` |
| Onde o beta é marcado no GitHub | `prerelease: true` + `make_latest: false`: `/releases/latest` continua só estável, e quem não optou nunca vê beta — inclusive versões antigas do app |
| Ordenação | SemVer: `42.0.0-beta.1 < 42.0.0-beta.2 < 42.0.0`; dono único segue `AppVersionComparison.kt` |
| Busca com opt-in ligado | `GET /releases?per_page=20`, ignora `draft`, escolhe a maior versão acima da atual |
| Desligar o opt-in estando num beta | Sem downgrade: fica no beta até sair estável maior |
| `packageVersion` do jpackage | Base numérica `X.Y.Z`; o resto leva a string completa |
| Destaque | Texto "beta" + selo; cor nunca informa sozinha |

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A0 | 2026-09-28 | este commit | Abrir o plano de execução | ✅ Concluída | revisão do diff (só documentação) |
| A1 | 2026-09-28 | este commit | Ordenação SemVer com pré-lançamento em `compareAppVersions` + `isPrereleaseVersion` | ✅ Concluída | `desktopTest --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.data.AppUpdate*" --tests "com.usagemonitor.update.*"` → 54 classes, 0 falhas; `AppVersionComparisonTest` `tests="10" failures="0"` |
| A2 | 2026-09-28 | este commit | Novidades abrem de beta para beta seguinte e de beta para estável | ✅ Concluída | `desktopTest --tests "com.usagemonitor.domain.ReleaseNotesTest"` → `tests="24" failures="0"` |
| A3 | 2026-09-28 | este commit | `AppUpdateInfo.isPrerelease` com default lido do sufixo da versão | ✅ Concluída | `desktopTest --tests "com.usagemonitor.domain.*"` → 355 casos, 0 falhas; `AppUpdateInfoTest` `tests="3" failures="0"` |
| B1 | 2026-09-28 | este commit | `GitHubReleaseDto` lê `prerelease` e `draft` (default `false`) | ✅ Concluída | `desktopTest --tests "com.usagemonitor.data.GitHubReleaseDtoTest"` → `tests="6" failures="0"` |
| B2 | 2026-09-28 | este commit | `RemoteApiDataSource.fetchGitHubReleases` (`/releases?per_page=20`) | ✅ Concluída | `desktopTest --tests "com.usagemonitor.data.RemoteApiDataSourceHttpTest"` → `tests="16" failures="0"` |
| B3 | 2026-09-28 | este commit | `getLatestAvailableUpdate(currentVersion, includePrereleases)`: canal beta pela listagem, sem rascunho, sem downgrade; use case repassa o flag | ✅ Concluída | `allTests` → 2378 casos, 0 falhas; `AppUpdateRepositoryImplTest` `tests="22" failures="0"` |
| B4 | 2026-09-28 | este commit | Preferência `receiveBetaUpdates` (default `false`) | ✅ Concluída | `desktopTest --tests "com.usagemonitor.BetaUpdatePreferencesTest"` → `tests="4" failures="0"` |
| B5 | 2026-09-28 | este commit | Coordenador lê o canal beta a cada verificação e reconsulta ao alternar o interruptor; `AutoUpdateController`/`AppViewModels` ligam a preferência (`DashboardViewModel` +3 linhas, 753 ≤ 800) | ✅ Concluída | `allTests` → 2384 casos, 0 falhas; `DashboardViewModelUpdateTest` `tests="7" failures="0"` |
| B6 | 2026-09-28 | este commit | Linux aceita só o sufixo `-beta.N` em `isValidLinuxVersionName` e no `linux-updater.sh`; cenários S8b/S8c | ✅ Concluída | `desktopTest --tests "com.usagemonitor.update.*"` → 0 falhas, `LinuxInstallLayoutTest` `tests="19" failures="0"`; `sh src/installer/linux/test/run-updater-scenarios.sh` (Git Bash) → 51 verificações, 0 falhas |
| C2 | 2026-09-28 | este commit | Toggle "Receber versões beta" em Configurações → Geral → Sistema, abaixo da atualização automática; toast das Configurações extraído (`SettingsDialogContent` voltou a ≤ 300 linhas); protótipo §Configurações e kit `Settings.jsx` | ✅ Concluída | `allTests` → 2390 casos, 0 falhas; `BetaUpdatesToggleTest` 4 casos |
| C3 | 2026-09-28 | este commit | Banner e balão da HUD dizem "beta" em todo estado; no balão o número da beta desce para o detalhe (não cabia na linha de 202dp — medido); selo não entrou na faixa: a palavra no título já informa sem cor, e o `AppBanner` não tem slot de selo. Protótipo §4c | ✅ Concluída | `allTests` → 2390 casos, 0 falhas (inclui `AppUpdateBannerTest` e `HudNotchTextFitTest` com `42.10.10-beta.12` de 100% a 150%) |
