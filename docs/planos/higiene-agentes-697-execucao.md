# Nenhum shell órfão; a orquestradora inspeciona toda parada de agente — issue #697

Procedência: Claude Opus 5.5 (orquestração, edição direta de texto de skill e agente), Claude Code CLI no Windows 11,
2026-11-27, branch `chore/issue-697-agent-hygiene`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Nenhum subagente fica vivo "waiting on shell" depois de devolver, e nenhuma parada de agente (concluído, parcial,
limite de turnos, esperando trabalho próprio) passa sem a orquestradora conferir o worktree na hora.

## Fatos confirmados (2026-11-27)

- Painel de agentes da sessão: `backend-dev` "waiting on 1 shell · T690 ignore synthetic live context · since 3:02 PM",
  com a #690 já mesclada (`964d1b5d`); encerrado por `TaskStop a4269cb97aa9038a9`.
- `frontend-dev` da #694 parou no limite de 15 turnos com "Aguardando … rodada de vermelho" em segundo plano; o
  `red4.log` (16:07) deu `SessionPulseTest` 10 testes, 0 falhas, e o agente ficou ocioso até 16:13 sem notificação.
- `backend-dev` da #694 devolveu duas vezes "aguardando … em segundo plano" antes do retorno final.
- `.claude/agents/*.md` e `.claude/skills/orchestrate/agents/*.md` não proíbem comando em segundo plano
  (`grep -n "background" .claude/agents/*.md`: nenhuma regra); a skill não manda conferir parada nem encerrar agente.

## Decisões

- Agentes de implementação e `test-runner`: todo comando em primeiro plano com timeout; proibido `run_in_background`,
  `&`, `nohup`, `Start-Process`. Mesma regra nos modelos de `.claude/skills/orchestrate/agents/`, que geram os agentes.
- Skill: seção "Agent hygiene" — toda notificação de agente leva a `git status`/diff/hora dos logs no worktree e a
  `ListAgents`; agente terminado ou parado com shell recebe `TaskStop`; espera passiva é proibida.
- `maxTurns` não muda.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Regra de primeiro plano nos agentes e nos modelos |
| A03 | sem harness (texto de skill); `grep -n "## Agent hygiene" .claude/skills/usage-monitor-workflow/SKILL.md` | seção presente antes de "Prototype gate"; aplicada nesta sessão: `TaskStop a4269cb97aa9038a9` encerrou o `backend-dev` da #690 |
| A03 | Seção "Agent hygiene" na skill `usage-monitor-workflow` |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `git log -1` | plano commitado (`9b9a8280`) |
| A02 | sem harness (texto de agente); `grep -n "#697" .claude/agents/*.md .claude/skills/orchestrate/agents/*.md` | 8 arquivos com a regra (4 agentes + 4 modelos) |

## Problemas em aberto e riscos

- Texto de skill e agente não tem harness: a regra vale pela leitura; a prova é o `ListAgents` limpo ao fim de cada tarefa.
