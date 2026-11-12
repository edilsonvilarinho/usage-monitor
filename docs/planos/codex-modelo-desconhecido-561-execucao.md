# Modelo desconhecido e lentidão no detalhe do Codex CLI — issue #561

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`fix/issue-561-codex-unknown-model`, base `48f9feee`, commits com a identidade `claude <claude@anthropic.com>`.
Implementação pelo agente `backend-dev` (modelo do frontmatter em `.claude/agents/`).

## Objetivo

A issue mostra o detalhe da sessão `01a1c80e…` com "unknown model" em 67% dos tokens e a abertura do detalhe
lenta. Corrigir a atribuição de modelo (causa medida) e medir a lentidão antes de mexer nela.

## Fatos confirmados (2026-11-12, `48f9feee`)

- O rollout principal da sessão (`~/.codex/sessions/2026/11/10/rollout-…-01a1c80e….jsonl`, 28,3 MB, 5863 linhas)
  tem 765 `token_usage_record` e **todos** têm `turn_context` anterior com o mesmo `turn_id` no mesmo arquivo
  (script `node` que percorre o arquivo inteiro: `ok: 765, never: 0, after: 0`). Lido inteiro, nenhum turno fica sem modelo.
- O índice (`~/.usage-monitor/codex-cli-history.db`, `sqlite3 -readonly`) gravou desse mesmo arquivo 549 turnos com
  `model NULL` (130,6M tokens) e 216 com `gpt-6.1-sol` (`thread_source = user`); somam os 765.
  Nos subagentes da sessão: 648 turnos `NULL` (45,5M). No índice inteiro: 7910 turnos `NULL` em 47 sessões.
- Causa: `CodexCliRolloutParser.parse` guarda os contextos num mapa local (`CodexCliRolloutParser.kt:50`,
  preenchido em `:97-110`, lido em `:127`). A indexação incremental (`LocalCodexCliSessionDataSource.parseFromOffset`,
  `:221-236`) parseia só as linhas novas e passa só `metadata`: um `token_usage_record` cujo `turn_context` ficou num
  trecho já indexado sai com `model = null`. Sessão longa e ao vivo é indexada em muitos trechos — daí os 67%.
- Versão do índice Codex: `CODEX_INDEX_VERSION = 1` em `codex_cli_index_meta` (`LocalCodexCliSessionDataSource.kt:682`,
  checada em `:345-359`). As linhas `NULL` já gravadas só se corrigem com reindexação.
- `LocalCodexCliSessionDataSource.kt` tem 689 linhas (teto 800; acima de 750 a próxima mudança começa extraindo).
- Lentidão: a consulta do detalhe (`readTurns`, 2188 linhas) leva 0,046 s no `sqlite3`; nenhuma sessão tem
  `originator` vazio (`62|0`), então `refreshMissingOriginators` não relê arquivo. `SqliteConnectionManager.useConnection`
  é `synchronized` e `indexRollouts` lê e parseia os rollouts **dentro** dele.

## Decisões

- Contexto de turno sobrevive entre trechos: o parser recebe os contextos conhecidos e devolve os que viu; o
  data source persiste o último contexto por arquivo e o repassa na leitura seguinte. Persistência em arquivo novo
  (o data source está a 689 linhas).
- `CODEX_INDEX_VERSION` sobe para 2: a migração existente (`migrateIndexVersion`, `:343-362`) apaga só
  `codex_cli_session_files`, e o upsert de turno (`:414-415`) atualiza só `request_ts` no conflito. O upsert passa a
  preencher `model` (e `cwd`) por `COALESCE(codex_cli_turns.model, excluded.model)` — corrige as 7910 linhas `NULL`
  na reindexação, sem ação do usuário e sem apagar turnos.
- Lentidão: **hipótese não confirmada** — o detalhe espera a trava da conexão enquanto `indexRollouts` lê arquivos.
  Nada muda nela sem medição (A03).

## Atividades

| Id | O quê | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/codex-modelo-desconhecido-561-execucao.md` |
| A02 | Contexto de turno entre trechos + reindexação (TDD: teste que parseia em dois trechos e exige o modelo) | parser, data source, arquivo novo de contextos |
| A03 | Medir a lentidão (tempo de espera da trava × consulta) antes de qualquer correção | data source / log |

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com as medições acima | — |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*"` → `CodexCliRolloutParserTest.kt:97`, `LocalCodexCliSessionDataSourceTest.kt:208` e `:229`: `expected:<gpt-test> but was:<null>`; verde: o mesmo comando → BUILD SUCCESSFUL, 666 testes, 0 falhas. Contextos em `codex_cli_turn_contexts` (`CodexCliTurnContextStore.kt`), `CODEX_INDEX_VERSION` 2, upsert com `COALESCE` em `model`/`cwd`; data source com 700 linhas | verde |

## Problemas em aberto e riscos

- A03 pode mostrar que a lentidão não é a trava; nesse caso o plano é revisto antes de mexer.
- Reindexar do zero relê todos os rollouts uma vez (28 MB o maior medido) na primeira abertura após a atualização.
