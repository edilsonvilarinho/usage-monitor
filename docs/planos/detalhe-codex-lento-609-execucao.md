# Detalhe da sessão Codex CLI lento ao abrir — issue #609

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-16, branch
`fix/issue-609-codex-detail-turn-rows`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Expandir uma sessão na janela "Codex CLI Sessions" demora (confirmado pelo usuário: a ação lenta é abrir o
detalhamento da sessão, não a janela nem a lista). O detalhe tem de abrir sem esperar a montagem de milhares de linhas.

## Fatos confirmados (2026-11-16, `56559f2f`)

- `~/.usage-monitor/diagnostics/breadcrumbs.jsonl`: "janela sessões Codex CLI pintada em 26–119 ms" (4 aberturas no
  dia). A janela em si não é o gargalo; não há medida da carga do detalhe.
- `~/.usage-monitor/codex-cli-history.db` (somente leitura, Python `sqlite3`): 23.875 turnos, 69 sessões; a sessão da
  captura (`01a1e032…`) tem **4.317 turnos**; `SELECT * FROM codex_cli_turns WHERE session_id=?` devolve 4.317 linhas
  em **14,3 ms** (índice `idx_codex_cli_turns_session_ts`). A leitura não explica a lentidão.
- `LocalCodexCliSessionDataSource.kt:136-148`: `readSession` lê **todos** os turnos (`readTurns(…, null)`), sem corte.
- `CodexCliSessionDetail.kt:203`: `CodexTurnTable` monta uma `AppDataRow` com 6 células por turno, `forEachIndexed`
  dentro de `Column + verticalScroll` (`:64-65`): 4.317 linhas ≈ 25.900 textos compostos e medidos de uma vez.
- `CodexCliSessionsViewModel.kt:268-276,293-300`: com a janela aberta, o laço ao vivo recarrega o detalhe a cada 5 s;
  sessão ativa muda `detail.turns` e recompõe a tabela inteira.
- `CLAUDE.md`, "Regras que continuam valendo": nenhum `Column + verticalScroll` vira `LazyColumn`.

## Decisões

- Corte na apresentação, não na leitura: gráficos e totais continuam sobre todos os turnos (leitura custa 14 ms).
- A tabela mostra as **200 respostas mais recentes** (ordem crescente de `seq` mantida, a cauda); acima das linhas,
  botão fantasma "Mostrar 200 anteriores (restam N)" acrescenta 200 por clique. Estado por sessão: reinicia ao trocar
  de sessão, sobrevive à recarga de 5 s.
- Sem `LazyColumn` (regra do `CLAUDE.md`); texto por `localized(…)`.

## Atividades

| Id | Atividade | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Corte da tabela de turnos em 200 + "mostrar anteriores" (TDD), medição antes/depois, mockup e `docs/presentation.md` | frontend-dev |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `python sqlite3` sobre `codex-cli-history.db` (somente leitura) | 4.317 turnos na sessão, consulta 14,3 ms |

## Problemas em aberto e riscos

- A causa é inferida do código e do volume (25.900 textos), não cronometrada na janela real; A02 mede a primeira
  composição com 4.317 turnos antes e depois e registra aqui. Se o "depois" não cair, a hipótese está errada.
- `refreshMissingOriginators` e o lock único do `SqliteConnectionManager` podem atrasar o detalhe enquanto o laço de
  5 s indexa; hoje 0 sessões sem originator (medido), fora do escopo.
