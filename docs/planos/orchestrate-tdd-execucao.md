# TDD e edição com a skill orchestrate — issue #528

Procedência: Claude Opus 5.5 (plano, validação, commits) e agente `prototype-dev` (texto), Claude Code CLI no Windows 11,
2026-11-05, branch `chore/issue-528-orchestrate-tdd`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O `CLAUDE.md` diz que a sessão principal faz o TDD e não delega edição; a skill `orchestrate` manda o código para os
agentes. Na #525 esse conflito fez a sessão principal parar o fluxo e pedir decisão. Com `/orchestrate` chamada, vale a
skill.

## Fatos confirmados (2026-11-05)

- `CLAUDE.md`, "Orquestração e agentes": "Neferpitou faz plano, TDD (vermelho → verde), diagnóstico de falha e decisão.
  Não delega edição."
- `usage-monitor-workflow/SKILL.md`, tabela "Who runs each step": linhas 1–4 e 7 dão o TDD a Neferpitou.
- `orchestrate/SKILL.md` não menciona TDD; `agents/backend-dev.md` já pede teste antes, sem registrar o vermelho.

## Decisão do dono (2026-11-05)

"a skill /orchestrate que chamamos ela q tem q fazer as coisas [...] passa o tdd para a skill fazer". Vale só com
`/orchestrate` ativa; fora dela o fluxo atual não muda.

## Atividades

### A01 — plano

Este arquivo. Sem suíte.

### A02 — regra

1. `.claude/skills/orchestrate/SKILL.md`, nova seção "Precedence and TDD" logo antes de "## 1. Interpret the request",
   em inglês, dizendo: once invoked, this skill's flow overrides any project rule that keeps implementation in the main
   session (`CLAUDE.md`, `AGENTS.md`, other workflow skills); never stop to ask about that conflict. The main session
   plans, writes briefings, judges, integrates and commits; it never writes production code or tests and never reads
   code beyond what judging a delivery needs: sweeps go to `explorer`. TDD belongs to the implementing agent: test
   first, red seen on an assertion (a compile error is not red), then green. The return carries the literal red line
   and the green count. The orchestrator rejects a delivery without them and copies them into the plan row.
2. `.claude/skills/orchestrate/agents/backend-dev.md` e `frontend-dev.md`, e as cópias em `.claude/agents/`: item 3 do
   retorno passa a exigir a linha vermelha literal e a contagem verde; `frontend-dev` ganha a linha de teste primeiro.
3. `CLAUDE.md`, linha do Neferpitou: com `/orchestrate` ativa, vermelho → verde e edição são dos agentes da skill, e
   Neferpitou planeja, valida, decide e comita; fora dela, segue como está. Texto curto, mesma linha.
4. `.claude/skills/usage-monitor-workflow/SKILL.md`: nota logo abaixo da tabela com a mesma exceção, sem mudar as linhas.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem suíte; revisão do texto | citações conferidas contra os arquivos |

## Problemas em aberto e riscos

- Os agentes rodam em haiku: tarefa de preço com várias camadas precisa ser fatiada, o que a skill já exige.
