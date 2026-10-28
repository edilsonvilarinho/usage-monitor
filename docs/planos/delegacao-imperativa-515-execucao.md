# Delegação imperativa ao test-runner e ao explorer — issue #515

Procedência: Claude Opus 5.5 (effort high), Claude Code CLI 2.1.312 no Windows 11, 2026-10-28, branch
`docs/issue-515-imperative-delegation`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

A #513 descreveu a delegação ("delega o que é longo"). A instrução padrão do Claude Code para a ferramenta `Agent`
diz para não criar subagente a não ser que o usuário peça ou cite o agente pelo nome. Regra descritiva perde para
essa instrução; regra imperativa no `CLAUDE.md`, que o próprio Claude Code declara prevalecer sobre o padrão, não.

## Fatos confirmados (2026-10-28, `8bb38b02`)

- Descrição da ferramenta `Agent` nesta sessão: "Do not spawn agents unless the user asks… Only use this tool when the
  user explicitly says to use a subagent, or names one of the available agent types."
- O `CLAUDE.md` entra no contexto marcado "These instructions OVERRIDE any default behavior".
- Sessão nova do usuário: `/status` com `@neferpitou`, `claude-opus-5-5`, `Shared project settings`.

## Decisões

- O texto nomeia o agente (`test-runner`, `explorer`) e diz que a regra **é** o pedido explícito do usuário,
  atendendo à letra da instrução padrão.
- Sem hook que force: a delegação é decisão do modelo; o piloto (#513) mede se a regra basta.

## Atividades

- A01: este plano.
- A02: `CLAUDE.md` e skill `usage-monitor-workflow` imperativos.

## Ponto de situação

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano | `gh issue create` → #515 |
| A02 | `CLAUDE.md` "Delegação obrigatória" e skill com a tabela obrigatória, nomeando `test-runner`/`explorer` | Sem harness. CI desta issue acompanhado pelo `test-runner` (resultado no comentário da issue) |

## Problemas em aberto e riscos

- Se mesmo assim a sessão rodar testes ou CI direto, o próximo passo é um hook, fora deste escopo.
