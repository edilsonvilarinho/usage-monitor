# Avisos `<synthetic>` não definem o contexto vivo — issue #690

Procedência: Claude Opus 5.5 (orquestração), implementação por `backend-dev`, Claude Code CLI no Windows 11,
2026-11-27, branch `fix/issue-690-synthetic-live-context`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Aviso que o Claude Code grava como `assistant` com `model: "<synthetic>"` (limite, erro 429, saldo) não pode
zerar o contexto vivo nem trocar o modelo do último turno, na lista e no detalhe da sessão.

## Fatos confirmados (2026-11-27, `6ff7ef9b`)

- `LocalCliSessionTranscripts.kt:168-190`: `toParsedTurn()` aceita toda linha `assistant` com `usage`.
- `LocalCliSessionIndexSql.kt:538-563`: `SELECT_LIVE_CONTEXTS_SQL`/`SELECT_LIVE_CONTEXT_BY_SESSION_SQL` pegam
  `MAX(seq)` com `is_sidechain = 0`, sem excluir `<synthetic>`.
- `ComputeCliSessionAnalyticsUseCase.kt:30-36`: detalhe usa `mainTurns.lastOrNull()`.
- `LocalCliSessionDataSource.kt:499,680`: sessão sem linha de contexto vivo vira `liveContextTokens = 0`;
  `CliSessionContextStatus.liveContextTokens` é `Long` não anulável.
- `LocalCliSessionIndexSchema.kt` (comentário de `migrateSchemaVersion`): reset de arquivos relê e reinsere por
  `INSERT OR IGNORE`, não apaga turnos já gravados. Filtrar no parser deixaria os turnos antigos no índice.
- `cli_sessions.turn_count` é gravado (`LocalCliSessionIndexSql.kt:56`) e recontado em 4 consultas.

## Decisões

- Filtro na **leitura** do contexto vivo, não no parser: as duas consultas e o use case do detalhe ignoram turno
  com `model = '<synthetic>'`. Vale para índice antigo e novo sem migração.
- Turnos `<synthetic>` **continuam contando** em "turns" e "per message": são registros do transcript, com tokens
  e custo zero; excluí-los exigiria migração de `turn_count` e das 4 consultas. Registrado em `docs/presentation.md`.
- Sessão cujo único `assistant` é aviso: sem turno real, cai no caminho já existente de sessão sem contexto vivo
  (`0`). O estado "indisponível" exige tornar `liveContextTokens` anulável da entidade à tela — fica para issue
  própria (ver riscos).

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Filtro `<synthetic>` no SQL do contexto vivo e no use case do detalhe + testes + `docs/presentation.md` |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `git log -1` | plano commitado |

## Problemas em aberto e riscos

- Critério "contexto indisponível" da #690 fica fora desta entrega; issue de continuação aberta para o estado anulável.
