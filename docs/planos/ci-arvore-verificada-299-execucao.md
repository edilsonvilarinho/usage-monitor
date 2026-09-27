# CI: o push na `main` reaproveita a árvore verificada no PR (#299) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-09-26 |
| **Issue** | [#299](https://github.com/edilsonvilarinho/usage-monitor/issues/299) |
| **Branch** | `perf/299-ci-verified-tree`, criada de `main` (`686eaa3`) |
| **PR alvo** | `main` |

## Contexto

A issue #299 aponta que o CI roda no PR e roda de novo quando o merge chega à `main`. Medido nos
runs reais:

| Verificação | Resultado |
|---|---|
| Push na `main` depois do merge | `tests` ~10–11 min em `windows-latest` (`BUILD SUCCESSFUL in 9m 52s`, run `36278024325`) + `installer-scenarios` + 3× `linux-updater-scenarios`, que no push rodavam **sempre** |
| Mesmo código? | Árvore do `refs/pull/N/merge` testado no PR × árvore do squash na `main`: **igual em 3 de 3** (run `36281452750` × `686eaa3`, `36277450509` × `f2a2c60`, `36204926203` × `a7ccc63`) |
| Proteção da `main` | nenhuma (`Branch not protected`, zero rulesets) — PR pode ser mergeado com a base adiantada |
| Visibilidade | repositório público: o custo é fila e concorrência de runner Windows, não minuto cobrado |
| O que só o push na `main` fazia | gravar o cache do Gradle (`cache-read-only` fora dela) e gerar cobertura Kover |

Os screenshots anexados à issue não foram abertos (anexo exige sessão web); a análise vem dos runs
via `gh`.

## Decisões do usuário

1. **Pular por árvore verificada**, em vez de remover o gatilho `push` ou ligar proteção de branch.
2. **Cobertura passa para o PR** (+6–7 s medidos por run).

## Desenho

- `verified-tree` (só `pull_request`, `needs` dos três jobs, só se a suíte rodou de fato): publica o
  artifact `ci-verified-tree-<tree>` com tree, commit de merge, PR e run. Retenção de 30 dias.
- `gate` (primeiro job, ubuntu, `actions: read`): no push, lê a árvore do `github.sha` e procura o
  artifact. Aceita só run de `pull_request`, `conclusion == success`, `path` do `ci.yml` e
  `head_repository_id == repository_id` — PR de fork edita o próprio `ci.yml` e forjaria o marcador.
  Falha de API é `skip=false`.
- `tests`, `installer-scenarios` e `linux-updater-scenarios` ganham `needs: gate`; com `skip=true`
  publicam **NAO EXECUTADA** com o link do run de PR (regra da issue #93).
- Continuam rodando tudo: base adiantada no merge (árvore diferente), push direto (bump de release),
  fork, marcador expirado.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Jobs `gate` e `verified-tree`, `needs`/motivo nos três jobs, cobertura em todo run que executa a suíte; CLAUDE.md, comentário do Kover e este plano | `actionlint -shellcheck= .github/workflows/ci.yml` (1.7.12); script do `gate` extraído do YAML e rodado contra a API real com `EVENT=push` e `SHA=686eaa3` | lint sem achado. Sem marcador: `skip=false`, "rodando tudo". Com o nome do artifact trocado por `test-reports` (existente, de run de PR verde): `skip=true` citando o run `36280893779`. `EVENT=pull_request`: `skip=false` |
| A2 | Run do PR deste trabalho publica o marcador; push do merge pula a suíte | CI do PR + push na `main` | pendente |
| A3 | Caso negativo: próximo bump de release (push direto) roda completo e grava cache/cobertura | push do bump | pendente |

## Problemas em aberto e riscos

- **Cache do Gradle**: a `main` só grava quando roda de verdade. Entre esses runs os PRs restauram o
  último cache gravado; dependência nova fica fria até o próximo run completo. Degrada para download,
  não para falha.
- **`pull_requests` vem vazio** no run de PR depois do merge (medido no A1): o motivo do "NAO
  EXECUTADA" cita o run, não o número do PR. O número está no arquivo do artifact.
- **Fora de escopo**: `ci-server.yml` (~10 s, não compensa) e a duplicação entre o push do bump de
  release e o job `verify` do `release-linux.yml`, que testam a mesma árvore em paralelo.
