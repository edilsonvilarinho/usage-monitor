# Release publicada antes dos assets vira "download falhou" — issue #582

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-12, branch
`fix/issue-582-update-asset-pending`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

A v42.3.0-beta.2 ficou pública antes de o instalador do Windows terminar de subir. O app que consultou a release nesse
intervalo mostrou "Beta failed · The download did not finish", texto falso: o download nem começou. Além disso, a
falha entrou no backoff. Duas correções: o workflow só publica a release depois de subir os assets, e o app trata
"artefato ainda sem digest" como versão disponível, não como falha.

## Fatos confirmados (2026-11-12, `77d1cca8`)

- `gh api repos/edilsonvilarinho/usage-monitor/releases/tags/v42.3.0-beta.2`: `published_at` 23:49:39Z. Todos os
  assets têm `created_at` 23:49:41Z; o `UsageMonitor-Setup-42.3.0-beta.2.exe` tem `updated_at` 23:49:48Z.
- `~/.usage-monitor/diagnostics/app.log`, linhas 835 (23:49:46Z, pid 62060) e 841 (23:50:18Z, pid 66940):
  `baixar atualização do app falhou: IllegalStateException: No verifiable NSIS installer was published for this platform.`
- A exceção nasce em `WindowsAppUpdateInstaller.kt:106`, antes do download: `selectArtifact` (`:164`) exige
  `sha256 != null`, que vem do `digest` do asset (`AppUpdateRepositoryImpl.kt:125`). O Linux segue o mesmo padrão
  (`LinuxAppUpdateInstaller.kt:147`, `:244`).
- `DashboardUpdateCoordinator.startUpdateDownload` leva qualquer falha de `prepare` para
  `registerUpdateFailure(DOWNLOAD)`, ou seja, `Failed` com backoff. O banner traduz `DOWNLOAD` como "The download did
  not finish" (`DashboardScreenWarnings.kt:552`).
- `release-linux.yml:575` usa `softprops/action-gh-release` sem `draft`: a release fica pública e os assets sobem
  depois. O app ignora rascunhos (`usage-monitor-release-beta`, "withdraw"), e `gh release edit --draft=false
  --prerelease --latest=false` já é o comando de restauração documentado.
- Às 23:5x, depois do upload, o mesmo app baixou e verificou a beta.2 ("Beta ready", captura do usuário).

## Decisões

- **Workflow:** a release é criada como rascunho (`draft: true`) e um passo seguinte publica com `gh release edit
  --draft=false`, `--prerelease`/`--latest` conforme o canal. Isso fecha a janela na origem.
- **App, defesa em profundidade:** `AppUpdateInstaller.hasInstallableArtifact(update)`. Sem artefato verificável,
  o coordenador publica `Available` (caminho manual), sem `Failed` e sem backoff; o próximo ciclo de verificação
  tenta de novo. O método é abstrato, sem default: um instalador novo precisa responder.
- **Hipótese não fechada:** a 2ª falha (30 s depois do upload) pode ser o `digest` preenchido com atraso ou cache da
  API. A defesa no app cobre os dois casos; a medição fica para a próxima beta (`published_at` contra `updated_at`).

## Atividades

| ID | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/` |
| A02 | Release criada como rascunho e publicada depois dos assets | `.github/workflows/release-linux.yml`, `docs/build-and-release.md` |
| A03 | `hasInstallableArtifact` no contrato, no coordenador e nos instaladores Windows/Linux (TDD) | `domain/repository/AppUpdateInstaller.kt`, `DashboardUpdateCoordinator.kt`, `update/*AppUpdateInstaller.kt`, testes |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | feito | Plano escrito; sem suíte (documento). |

## Problemas em aberto e riscos

- Reexecutar o workflow de uma beta retirada (rascunho) volta a publicá-la. Hoje acontece o mesmo, porque o
  `softprops` reaproveita a release existente; retirar continua sendo pelo `gh release edit --draft=true` depois do run.
- O passo de publicação não tem suíte: só é validado por parse e pela próxima release real.
