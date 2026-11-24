# `desktop-linux` bloqueia o merge — issue #649

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `ci/issue-649-linux-gate` (worktree `.claude/worktrees/linux-gate-649`, base `origin/main` `c24f5f41`), commits
com a identidade `claude <claude@anthropic.com>`. Parte da #644; fecha a pendência do gate da #342.

## Objetivo

Falha que só aparece no Linux passa a impedir o merge, sem trazer instabilidade para dentro do `ci-ok`.

## Fatos confirmados (2026-11-24)

- 150 últimos runs do `ci.yml` (desde 2026-10-28, `gh api …/runs/<id>/jobs`): `desktop-linux` 128 verdes, 17 pulados,
  2 cancelados, 3 vermelhos. Um dos vermelhos (42556374159, jline) reprovou também no Windows. 21 verdes desde o último.
- Os outros dois reprovaram só no Linux, com 3 testes diferentes:
  - run 44303839875 (`main`, 2026-11-18), artefato `test-reports-linux`:
    - `AppSettingsFeedbackTest`: `BackingStoreException: Couldn't get file lock` em `FileSystemPreferences.removeNode`,
      chamado por `SettingsTestHarness.close`. No Linux o `java.util.prefs` usa um arquivo de trava por usuário, e os 3
      forks (`-PtestForks=3`) o disputam. No Windows a implementação é o registro.
    - `DashboardViewModelTest` (`collects and refreshes multiple Anthropic profiles independently`): `expected:<[profile-a,
      profile-b]> but was:<[profile-a]>`. O teste espera `snapshots.size == 2` e lê `uiState` em seguida, sem esperar a
      publicação. `awaitSettledState` existe para isso (`CLAUDE.md`, "CI e testes").
  - run 43297153508 (2026-11-12): `HistoryViewModelTest` (`selects most recent account and reloads history when account
    changes`), `AssertionError` na linha 182. Artefato `test-reports-linux` não disponível; valores não lidos.
- `HistoryViewModelTest.awaitNonLoading` aceita qualquer estado que não seja `Loading`, e `awaitInvocations` volta quando
  o repositório **começa** a ser lido.

## Hipóteses ativas

- `HistoryViewModelTest`: depois de `selectAccount`, o helper devolve o `Success` anterior. Compatível com o código dos
  helpers; a confirmar em A03, segurando o `FakeRepo` depois do incremento.

## Decisões

- **Preferências em memória nos testes fora do Windows**, por `java.util.prefs.PreferencesFactory` de `desktopTest`: sem
  arquivo, não há trava. No Windows nada muda; a suíte de referência continua no registro.
- **Espera pelo estado esperado**, nunca `sleep` maior nem `retry` de teste.
- **O gate só muda depois das três correções**, no último commit. Nenhum teste é pulado para o gate passar.

## Atividades

- **A01** — este plano.
- **A02** — preferências em memória nos testes fora do Windows.
- **A03** — `HistoryViewModelTest` e `DashboardViewModelTest` esperam o estado que afirmam.
- **A04** — `ci.yml`: sem `continue-on-error`, `desktop-linux` em `RESULTS`; `CLAUDE.md` e `docs/build-and-release.md`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `gh api …/jobs` dos 150 runs e `gh run download 44303839875 -n test-reports-linux` | 3 vermelhos, 2 só no Linux; causas acima |
| A02 | `gradlew.bat desktopTest --tests "*InMemoryPreferences*" …` | vermelho (stub): `InMemoryPreferencesFactoryTest.kt:18 expected:<valor> but was:<null>`; verde 5/5. Em Linux só o run do PR prova |
| A03 | `gradlew.bat desktopTest --tests "*InMemoryPreferences*" --tests "*HistoryViewModelTest*" --tests "*DashboardViewModelTest*" --tests "*AppSettingsFeedbackTest*" --tests "*ArchitectureRulesTest*"` | vermelho com o `FakeRepo` segurado depois do incremento (temporário): `HistoryViewModelTest.kt:206 expected:<…user-b…> but was:<…user-a…>` — hipótese confirmada. `DashboardViewModelTest`: o vermelho é o do CI (run 44303839875), não reproduzido localmente. Verde 96/96 |

## Problemas em aberto e riscos

- A suíte no Linux nunca rodou localmente nesta máquina; a prova de A02 é o run do PR.
- Um quarto teste instável pode existir. A conta de incidências do fluxo vale: na 4ª, para.
