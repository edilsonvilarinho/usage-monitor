# Limpeza de contexto: descrição de skill e retrato vencido do CLAUDE.md — issue #511

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`docs/issue-511-ctx-cleanup`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Itens aprovados pelo dono na skill `usage-monitor-token-cleanup` que tocam o repositório.

## Fatos confirmados (2026-10-28, `983cfc4d`)

- `measure_context.ps1`: descrição de `usage-monitor-release-video` com 556 caracteres (~159 tokens estimados, toda sessão).
- `CLAUDE.md:211-213` cita `TeamUsageViewModel`/`DashboardViewModel` entre 750 e 800 linhas e `TeamUsageScreen` com 749;
  `wc -l` hoje: 743, 742 e 675.

## Decisões

- A descrição mantém os gatilhos de uso em português (release, lançamento, insta/tiktok, descrição de post, tour).
- No `CLAUDE.md` fica a regra (entre 750 e 800 linhas, a próxima mudança começa extraindo); o retrato numérico sai,
  porque vence a cada commit e o `ArchitectureRulesTest` já mede o teto.

## Atividades

- A01 — este plano.
- A02 — as duas edições.

## Ponto de situação

| Atividade | Estado | Validação |
|---|---|---|
| A01 | feito | sem harness de teste (documentação); `git diff --stat` mostra só este arquivo |

## Problemas em aberto e riscos

- Descrição mais curta pode reduzir o disparo automático da skill; os gatilhos explícitos foram mantidos.
