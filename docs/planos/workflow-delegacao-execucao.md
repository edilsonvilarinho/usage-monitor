# Delegação de agentes na usage-monitor-workflow — issue #530

Procedência: Claude Opus 5.5 (plano, validação, commits) e agente `prototype-dev` (texto), Claude Code CLI no Windows 11,
2026-11-05, branch `chore/issue-530-workflow-delegation`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O #529 copiou o fluxo do projeto para dentro da `orchestrate`. Decisão do dono: a `usage-monitor-workflow` volta a ser o
fluxo único, sempre ativo, e traz o modo de trabalhar com agentes da `orchestrate`, sem portal. A `orchestrate` fica como
ferramenta, sem a cópia.

## Fatos confirmados (2026-11-05, `32615b32`)

- `.claude/skills/orchestrate/SKILL.md`: "## Precedence and TDD" e "## Project delivery flow" ocupam as linhas 52 a 248.
- A `orchestrate` carrega o estado dos agentes com `` !`node ${CLAUDE_SKILL_DIR}/setup/agentes.mjs verificar || true` ``.
- `emit.mjs` funciona sem portal com `--sessao`; `LIMITE_REPROVACOES = { haiku: 10, sonnet: 3 }` em `portal/emit.mjs:17`.
- `raizRepo()` é `PORTAL_RAIZ`, senão `CLAUDE_PROJECT_DIR`, senão o diretório atual: de um worktree, o evento vai para o
  `.claude/escritorio` dele.

## Decisões

- Sem portal: nenhum `start.mjs`. `emit.mjs` fica só como contador de devoluções e limites, porque é ele que imprime a ação.
- `test-runner` em sonnet e `explorer` sem `effort` ficam (#513); não vale o "tudo em haiku" da `orchestrate`.

## Atividades

### A01 — plano

Este arquivo. Sem suíte.

### A02 — delegação na `usage-monitor-workflow`

Só `.claude/skills/usage-monitor-workflow/SKILL.md`, em inglês. Fonte: `.claude/skills/orchestrate/SKILL.md`, seções 0, 3,
4, 5 e 6.

1. Frontmatter: `allowed-tools` com `Bash(node .claude/skills/orchestrate/setup/agentes.mjs *)` e
   `Bash(node .claude/skills/orchestrate/portal/emit.mjs *)`. Mantém `name` e `description`, acrescentando à descrição que
   agentes implementam.
2. Depois do título, bloco `Agents:` com `` !`node .claude/skills/orchestrate/setup/agentes.mjs verificar || true` `` e a
   seção de primeira execução (`PENDENTE`: perguntar, `agentes.mjs instalar` ou `dispensar`, corrigir o contexto gerado).
3. Tabela "Who runs each step" trocada por papéis: Neferpitou (issue, os dois planos, briefing, worktree, validação,
   commits, push, PR, merge, limpeza, comentário; nunca escreve código de produção nem teste); agente que implementa
   (`backend-dev`, `frontend-dev`, `prototype-dev`: vermelho → verde e correção de CI vermelho, no worktree do prompt);
   `test-runner` (passos 5 e 7); `explorer` (varredura). Sai a nota "Exception while `/orchestrate` is active".
4. Seção nova "Route" da seção 3 da fonte: tabela de trabalho por agente, modelo só no frontmatter, sem `model` nem
   `effort` na delegação, tarefa grande vira várias pequenas, edição direta só para ajuste pontual em arquivo conhecido.
   Ajustar a frase "every subagent runs on haiku" para a decisão deste plano.
5. Seção nova "Delegate" da seção 4 da fonte: prompt de até 12 linhas no formato padrão, briefing em arquivo, proibições,
   até 4 agentes, `isolation: "worktree"` para código em paralelo com a exceção de pastas exclusivas.
6. Seção nova "Validate and send back" da seção 5 da fonte, com os comandos `emit.mjs tarefa`, `veredito` e `escalada`, cada
   um com `--sessao ${CLAUDE_SESSION_ID}`, rodados do checkout principal, com o porquê (`raizRepo()`). Sem `fala`.
   Devolução e incidência de CI são contagens separadas.
7. Seção nova "Prototype gate" da seção 6 da fonte, sem portal: abrir `prototipos/<entrega>/index.html` no navegador;
   mantém `usage-monitor-visual-options` e o design system como fonte.
8. Passo 4 (TDD): pertence ao agente; compilar não é vermelho; o retorno traz a linha vermelha literal e a contagem verde,
   que Neferpitou copia para a linha do plano; sem elas a entrega é devolvida. Passo 7: a correção volta ao agente.

Passos 1, 2, 3, 5, 6 e 8 mantêm os comandos atuais. Nenhuma menção a `start.mjs` ou portal visual.

### A03 — `orchestrate` sem a cópia e `CLAUDE.md`

1. `.claude/skills/orchestrate/SKILL.md`: remove "## Precedence and TDD" e "## Project delivery flow" inteiras, e a frase
   sobre "Project delivery flow" em "## 7. Wrap up". O resto não muda.
2. `CLAUDE.md`, "Orquestração e agentes": linha do Neferpitou passa a ser "Neferpitou faz plano, diagnóstico de falha,
   decisão e commit. Vermelho → verde e edição são dos agentes de implementação (`backend-dev`, `frontend-dev`,
   `prototype-dev`)."
3. `CLAUDE.md`, "Delegação obrigatória": novo item "**sempre o agente de implementação** para código e teste; direto só
   ajuste pontual em arquivo conhecido".
4. `CLAUDE.md`, "Fluxo de trabalho": no passo 2, TDD pelos agentes; na linha "Roteiro executável", sai o ponteiro para
   "Project delivery flow". Só essas linhas.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem suíte; revisão do texto | linhas e comandos conferidos contra a `main` |
| A02 | sem suíte; diff lido item a item; `grep -c start.mjs` = 0, `grep -c "Project delivery flow"` = 0, `grep -c emit.mjs` = 8 | oito itens aplicados, 0 devoluções |
| A03 | sem suíte; diff lido; `node --test` de `setup/agentes.test.mjs` e `portal/cli.test.mjs` pelo `test-runner` | quatro itens aplicados; 13 passam, 1 falha, a mesma CRLF da `main` |

## Problemas em aberto e riscos

- A regra só fica provada quando a #525 rodar por este fluxo.
- `agentes.test.mjs` segue com 1 falha, a mesma da `main` (CRLF).
