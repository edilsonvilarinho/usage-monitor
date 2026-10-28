# Main local atualizada depois de cada merge — issue #509

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`docs/issue-509-pull-main`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O encerramento do fluxo (passo 6 do `CLAUDE.md`, passo 8 da skill `usage-monitor-workflow`) remove worktree e branch,
mas não atualiza a `main` local; o usuário fica com o checkout atrás da `origin/main`. Pedido do usuário: atualizar sempre.

## Fatos confirmados (2026-10-28, `1e100fca`)

- Depois do merge do PR #508, `git status -sb` na `main`: `## main...origin/main [behind 11]`.
- `git pull --ff-only` levou a `main` a `1e100fca` sem conflito; os arquivos não rastreados do checkout ficaram intactos.
- O passo 8 da skill termina em `git fetch --prune`, que atualiza só as referências remotas.

## Decisões

- `git pull --ff-only`: nunca cria merge local nem reescreve a `main`; se a `main` local divergir, o comando falha e
  Claude relata em vez de forçar.

## Atividades

- A01 — este plano.
- A02 — regra no `CLAUDE.md` e comando na skill.

## Ponto de situação

| Atividade | Estado | Validação |
|---|---|---|
| A01 | feito | sem harness de teste (documentação); `git diff --stat` mostra só este arquivo |

## Problemas em aberto e riscos

- `main` local com commits próprios ou alteração rastreada conflitante: o `--ff-only` falha por desenho; o caso é relatado.
