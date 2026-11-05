# Limite de escalada e test-runner em Haiku — issue #532

Procedência: Claude Opus 5.5 (plano, validação, commits) e agente `prototype-dev` (código e texto), Claude Code CLI no
Windows 11, 2026-11-05, branch `chore/issue-532-agent-limits`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Economizar cota. Decisão do dono: Haiku 5.5 high executa, e Sonnet 5.5 high só entra quando as validações falham muito.

## Fatos confirmados (2026-11-05, `6157df00`)

- `portal/emit.mjs:17`: `LIMITE_REPROVACOES = { haiku: 10, sonnet: 3 }`.
- `portal/cli.test.mjs:166-172,202` afirma `/10`; `orchestrate/SKILL.md:144` e `usage-monitor-workflow/SKILL.md:117` dizem
  "10th rejection"; a descrição da `orchestrate` também cita 10.
- `.claude/agents/test-runner.md`: `model: sonnet`, `effort: medium`. Modelo real conferido nos transcripts da sessão
  (`claude-sonnet-5-5`). O modelo `orchestrate/agents/test-runner.md` já é `haiku`/`high`.

## Decisões

- Limite em Haiku = 3, igual ao de Sonnet. Número escolhido sem medição; a revisar com uso real.

## Atividades

### A01 — plano

Este arquivo. Sem suíte.

### A02 — limite

TDD em `.claude/skills/orchestrate/portal/cli.test.mjs`: trocar as asserções para o limite 3 (laço das devoluções,
`2/3` no `escalada` recusado, `3/3` com a ação de escalar, `1/3` na linha 202) e ver vermelho; depois
`LIMITE_REPROVACOES.haiku = 3` em `emit.mjs`. Texto: "3rd rejection on haiku" em `orchestrate/SKILL.md` e
`usage-monitor-workflow/SKILL.md`, descrição da `orchestrate` e o "(haiku 10, sonnet 3)" da workflow.

### A03 — test-runner

`.claude/agents/test-runner.md`: `model: haiku`, `effort: high`. `usage-monitor-workflow/SKILL.md`, seção "Route": o
`test-runner` entra junto dos implementadores em haiku high, e a frase diz que a decisão é desta issue, não da #513.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem suíte; revisão do texto | linhas conferidas contra a `main` |
| A02 | vermelho: `node --test .claude/skills/orchestrate/portal/cli.test.mjs` falha em `/reprovação 1/3 no haiku.*devolver ao mesmo agente/` (saída real `reprovação 1/10`); verde: mesmo comando; suíte do portal pelo `test-runner` | vermelho na asserção; 8 de 8 verdes; portal 35 de 35 |

## Problemas em aberto e riscos

- Haiku no `test-runner` pode resumir falha pior que Sonnet. Sem medição ainda.
- A economia real continua sem medir (#513).
