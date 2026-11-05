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

### A03 — fluxo do projeto dentro da skill

Pedido do dono (2026-11-05): "pegar a nossa skill workflow e ajustar a skill orchestrate adicionando a nossa skill".

1. `.claude/skills/orchestrate/SKILL.md`, nova seção "## Project delivery flow" logo depois de "## Precedence and TDD", em
   inglês. Traz os passos 1 a 8 de `.claude/skills/usage-monitor-workflow/SKILL.md` com os comandos literais (issue,
   plano, worktree, TDD, testes locais, PR com auto-merge, CI e parada na 4ª incidência, limpeza e comentário), e troca a
   tabela "Who runs each step" por esta divisão:
   - orquestrador: issue, os dois planos, worktree, briefing, validação, commits, push, PR, merge, limpeza, comentário;
   - agente que implementa: vermelho → verde e correção de CI vermelho, no worktree indicado no prompt;
   - `test-runner`: testes locais, relatório, cobertura do diff, `gh run watch`, `--log-failed`, `ci-incidents.mjs`;
   - `explorer`: varredura.
   Dizer que incidência de CI (`ci-incidents.mjs`) e devolução de entrega (`emit.mjs`) são contagens separadas.
2. Mesma seção: `emit.mjs` roda sempre a partir do checkout principal, nunca de um worktree. `dirEscritorio(raizRepo())`
   usa o repositório do diretório atual: de um worktree, o evento vai para o `.claude/escritorio` dele e o portal não vê
   (medido nesta sessão: tarefas A02 a A08 e T1 não apareceram no quadro).
3. Seção "## 7. Wrap up": apontar para o passo final da nova seção.
4. `.claude/skills/usage-monitor-workflow/SKILL.md`: a nota abaixo da tabela passa a dizer que, com `/orchestrate` ativa, o
   roteiro é a seção "Project delivery flow" da skill `orchestrate`.
5. `CLAUDE.md`, seção "Fluxo de trabalho", linha do "Roteiro executável": acrescentar que, com `/orchestrate`, o roteiro é
   o da mesma seção. Só essa linha.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem suíte; revisão do texto | citações conferidas contra os arquivos |
| A02 | `git diff` lido item a item; `node --test .claude/skills/orchestrate/setup/agentes.test.mjs` | quatro itens aplicados; 5 passam, 1 falha, igual à `main` (CRLF do `autocrlf` no modelo) |

## Problemas em aberto e riscos

- Os agentes rodam em haiku: tarefa de preço com várias camadas precisa ser fatiada, o que a skill já exige.
