# Janela de sessões do Codex no padrão da janela Anthropic — issue #560

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`feat/issue-560-codex-anthropic-layout`, base `48f9feee`, commits com a identidade `claude <claude@anthropic.com>`.
Implementação pelos agentes `backend-dev` e `frontend-dev` (modelo do frontmatter em `.claude/agents/`).

## Objetivo

Os prints da issue põem lado a lado "Codex CLI Sessions — Padrão" e "CLI Sessions — Padrão" (Anthropic): cabeçalho,
cartões, colunas, linhas e o Resumo divergem. O dono escolheu a opção **AO5 · Espelho + eixo Agente** da rodada AO
(galeria `prototipos/issue-560/`, cópia em `docs/planos/issue-560-visual/ao5.html`): o Codex copia a janela Anthropic
peça por peça; **Respostas** ocupa o lugar do Custo (o Codex não tem tarifa) e o Resumo ganha o eixo **Agente**
(sessão principal × subagentes).

## Fatos confirmados (2026-11-12, `48f9feee`)

- Anthropic: cabeçalho `CliSessionsScreen.kt:351-429` (rótulo de perfil `:387`, `LiveBadge` `:398`, botões com contorno
  `:412-428`), cartões `:437-476`, linha `CliSessionRow.kt:213-371`. Resumo `CliUsageBreakdownPane.kt:106` com eixos,
  filtro, ordenação e paginação (`CliUsageBreakdownPaging.kt`), tabelas `CliUsageBreakdownTables.kt`.
- Codex: cabeçalho `CodexCliSessionsScreen.kt:142-179` (sem rótulo de perfil; CSV/JSON em `GHOST`), linha `:402-507`
  com Respostas e Modelo na grade (8 colunas; a última é cortada no print), Resumo próprio
  `CodexCliSessionsSummaryPane.kt:103-169` ("Por projeto"/"Por modelo" empilhados, sem filtro nem paginação).
- O Resumo Anthropic recebe só `CliUsageBreakdown` (`domain/entity/CliUsageBreakdown.kt:104`): `byProject`, `byModel`,
  `byBranch`, `byMember`, `byTool`, `heatmap`. Eixo vazio não vira aba (`availableBreakdownAxes`,
  `CliUsageBreakdownPaging.kt:75-100`). `BreakdownAxis.MEMBER` existe e tem rótulo "Integrante"
  (`CliUsageBreakdownLabels.kt:58,76`).
- `CodexCliSessionTurn` traz `threadSource` (`user`/`subagent`), `model`, `cwd`, `ts`, `usage`
  (`CodexCliSessionModels.kt:41-54`). Medido no índice real: a sessão `01a1c80e…` tem 765 respostas da sessão
  principal (175,5M tokens) e 1423 de subagentes (87,6M).
- `gpt-6.1-*` e `gpt-6-luna` não estão em `ModelPricingTable` (`ModelPricingTable.kt:66` devolve `null`).

## Decisões

- **Um Resumo só.** O Codex passa a usar `CliUsageBreakdownPane`: um mapeador puro (domain) monta `CliUsageBreakdown`
  a partir dos turnos do período — projeto, modelo, atividade (hora local BRT) e agente. `CodexCliSessionsSummaryPane`
  sai do Resumo.
- **Eixo Agente sem valor novo de enum** (regra do `CLAUDE.md`): os buckets de agente entram em `byMember`, e o rótulo da
  aba vira parâmetro (padrão "Integrante"; o Codex passa "Agente"). Rótulos das linhas: "Sessão principal" e "Subagentes".
- **Eixos sem dado não aparecem** (regra existente do `availableBreakdownAxes`), e isso vale mais que o protótipo, que os
  mostrava com "sem dado medido": Branch e Ferramentas não são indexados no Codex.
- **Sem tarifa = Respostas.** Cartão e coluna "Respostas" no lugar do Custo; os cartões de custo do Resumo dão lugar a
  Respostas, Reuso de cache e Respostas por sessão quando o breakdown não tem nenhum turno com tarifa.
- Linha 2 da sessão: "● N respostas · M turnos · app · origem · versão"; linha 3 "Modelos (% dos tokens)". Modelo sai
  da grade. Rótulo "Tokens (com cache)".

## Atividades

| Id | O quê | Onde |
|---|---|---|
| A01 | Este plano + cópia do protótipo escolhido | `docs/planos/` |
| A02 | Mapeador Codex → `CliUsageBreakdown` (projeto, modelo, atividade, agente) + leitura dos turnos do período | domain, data source Codex, view model |
| A03 | Resumo do Codex pelo `CliUsageBreakdownPane` (rótulo do eixo, cartões sem tarifa) | `CliUsageBreakdown*.kt`, `CodexCliSessionsScreen.kt` |
| A04 | Cabeçalho, cartões, colunas e linhas no padrão Anthropic | `CodexCliSessions*.kt` |
| A05 | Protótipo visual, design system e histórico da rodada AO | `prototipo-visual-opencode.html`, `docs/presentation.md`, skill `usage-monitor-visual-options` |

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito; protótipo AO5 copiado para `docs/planos/issue-560-visual/ao5.html` | — |

## Problemas em aberto e riscos

- Mudança de tela: o PR diz em que plataforma foi testado; nenhum host de janela muda (#342).
- Turnos de 30 dias lidos de uma vez para montar o Resumo (~14 mil linhas medidas no índice real).
