# Commit e remoção em worktree — issue #498

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`fix/issue-498-worktree-skills`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Dois defeitos achados na execução da #496 (`fluxo-automatizado-496-execucao.md`, "Desvios"): o script de commit da
skill `usage-monitor-commit-push` não roda em worktree, e a remoção da worktree no passo 8 da skill
`usage-monitor-workflow` falha com caminho longo. O fluxo trabalha sempre em worktree, então os dois quebram todo PR.

## Fatos confirmados (2026-10-28, `69aeb3cf`)

- `commit_and_push.ps1` numa worktree: `error: could not lock config file .git/config: No such file or directory`
  (PR #497). O script lê, grava e restaura `user.name`/`user.email` com `git config --file .git/config` (linhas 89,
  111, 115, 156, 161); numa worktree `.git` é um arquivo que aponta para `<repo>/.git/worktrees/<nome>`.
- `.codex/skills/usage-monitor-commit-push/scripts/commit_and_push.ps1` é idêntico (`diff -rq`: só `SKILL.md` e
  `agents/` diferem).
- `git worktree remove .claude/worktrees/workflow-496` (git 2.56.0.windows.4, `core.longpaths` não configurado):
  `error: failed to delete '…/workflow-496': Filename too long`, com `server/node_modules` instalado pelo `npm ci`. O
  registro da worktree saiu e a pasta ficou; removida com `Remove-Item -LiteralPath '\?\…' -Recurse -Force`.
- Pester 6.2.0 disponível no `pwsh` desta máquina.

## Decisões

- **Identidade por `git -c user.name=… -c user.email=… commit`**, sem gravar nem restaurar config. Vale igual no
  checkout principal e em worktree, e nada sobra na config se o processo morrer no meio (o `finally` atual não cobre
  um `kill`). Os parâmetros `-TempUserName`/`-TempUserEmail` continuam.
- **Remoção com `git -c core.longpaths=true worktree remove`**, por comando, sem mudar a config do usuário.

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | TDD: `commit_and_push.Tests.ps1` (Pester) vermelho no script atual; identidade por `-c`; espelho `.codex/` |
| A03 | Passo 8 da skill `usage-monitor-workflow` com `core.longpaths`, medido antes e depois |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com os fatos medidos na #496 | `gh issue create` → #498; `diff -rq .claude/skills/usage-monitor-commit-push .codex/skills/usage-monitor-commit-push` → só `SKILL.md` e `agents/` |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| O teste Pester não roda no CI (nenhum job executa Pester); vale como teste local da skill | aceito |
