# Remoção de worktree com a sessão dentro dela — issue #500

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`fix/issue-500-worktree-cwd`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O passo 8 da skill `usage-monitor-workflow` remove a worktree depois do merge. Na limpeza do PR #499 a remoção falhou
com `Permission denied` mesmo com `core.longpaths` (#498): a pasta era o diretório atual de um processo da sessão.

## Fatos confirmados (2026-10-28, `6d504443`)

- Limpeza do PR #499: `git -c core.longpaths=true worktree remove .claude/worktrees/worktree-fix-498` (rodado no Bash
  com `cd` para a raiz) → exit 255, `failed to delete '…/worktree-fix-498': Permission denied`. `git worktree list` sem
  a entrada, pasta vazia; `rmdir` apagou depois que o diretório da sessão saiu dela. A ferramenta PowerShell do Claude
  Code mantém o diretório entre chamadas, e a última chamada dela tinha rodado dentro da worktree.
- Reprodução em repositório temporário: `Start-Process pwsh -WorkingDirectory <wt>` segurando a pasta →
  `git -c core.longpaths=true worktree remove <wt>` exit 255 `Permission denied`, registro 0, pasta com 0 itens;
  `Stop-Process` e `Remove-Item -LiteralPath <wt>` → pasta removida.

## Decisões

- **Sair antes de remover:** o passo 8 manda tirar o diretório atual das sessões PowerShell **e** Bash da worktree
  (`Set-Location`/`cd` para a raiz, cada um na sua ferramenta) antes do `git worktree remove`.
- **Recuperação documentada:** se mesmo assim sobrar só a pasta vazia e o registro tiver saído, `git worktree prune` e
  remover a pasta depois de sair dela — nunca `--force` em cima de conteúdo que não foi conferido.

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | Passo 8 da skill `usage-monitor-workflow` |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com a falha do #499 e a reprodução | `gh issue create` → #500; reprodução descrita em "Fatos confirmados" |
| A02 | Passo 8 da skill `usage-monitor-workflow`: tirar o diretório das sessões PowerShell e Bash da worktree, cada uma na sua ferramenta, antes do `git worktree remove`; recuperação com `git worktree prune` e remoção só de pasta vazia ou com ignorados da branch. Sem harness de teste (texto de skill) | Reprodução do plano (exit 255 `Permission denied` com processo dentro; removida após encerrá-lo). Validação final: limpeza deste PR seguindo o passo novo, registrada no comentário da issue |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| Outro processo fora da sessão (editor, explorador de arquivos) com a pasta aberta dá o mesmo erro; a recuperação cobre, a prevenção não | aceito |
