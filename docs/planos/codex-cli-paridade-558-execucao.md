# Paridade da janela de sessões do Codex CLI com a do Anthropic — issue #558

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`feat/issue-558-codex-cli-parity`, base `9b51052a`, commits com a identidade `claude <claude@anthropic.com>`.
Implementação pelos agentes `backend-dev` e `frontend-dev` (modelo do frontmatter em `.claude/agents/`).

## Objetivo

A issue compara duas capturas: a janela "Codex CLI sessions" e o card "Codex" mostram menos que a janela
"CLI Sessions — Padrão" e o card "Anthropic — Padrão". Escopo aprovado pelo dono (2026-11-12): **paridade sem
custo** — tudo o que os dados do Codex já sustentam; custo e linha de saúde ficam fora por falta de tarifa.

## Fatos confirmados (2026-11-12, `9b51052a`)

- Período: Anthropic usa `CliSessionRange` (`LAST_5H`, `LAST_7D`, `LAST_30D`, `ALL`;
  `domain/entity/CliSessionRange.kt:27`), rótulos em `CliSessionsFormatting.kt:135-142`. Codex tem enum próprio
  `CodexCliSessionRange` (`LAST_5H`, `LAST_7D`, `ALL`; `CodexCliSessionsViewModel.kt:32`), rótulos fixos "5h/7d/All"
  e "CSV"/"JSON" em `CodexCliSessionsScreen.kt:156-176`. Usado em 8 arquivos (`grep -rln CodexCliSessionRange src`).
- Exportação Anthropic rotulada em `UsageExportRequests.kt:237-243` ("Export CSV", "Export JSON").
- Resumo Codex (`CodexCliSessionsSummaryPane.kt:42-59`): só sessões, tokens e vazão. Anthropic tem tempo ativo
  (`CliSessionsScreen.kt:471-475`) e a linha `in · out · cache read · cache write` (`CliSessionsFormatting.kt:275-285`).
- Tokens por resposta já indexados: `codex_cli_turns` tem `input_tokens`, `cached_input_tokens`,
  `cache_write_input_tokens`, `output_tokens`, `ts`, `request_ts` (`LocalCodexCliSessionDataSource.kt:304-326`).
- Tempo ativo do Anthropic: `activeTimeMillisOf` (`CliSessionModels.kt:235`) soma intervalos entre turnos
  consecutivos `< TURN_GAP_CUTOFF_MILLIS` (`:212`). `CodexCliSessionSummary` não tem campo de tempo ativo.
- Linha Codex: sem coluna de tempo ativo e sem botão de copiar id (Anthropic: `CliSessionRow.kt:274`,
  `CliSessionsFormatting.kt:168-174`). Rodapé Codex "Updated" (`CodexCliSessionsScreen.kt:112-117`); Anthropic
  "last change" (`CliSessionsFormatting.kt:153-162`).
- Título: janela Codex fixa em "Codex CLI sessions" (`ModalWindowsHost.kt:101`); Anthropic "CLI Sessions — Padrão".
  Card: `displayTitle()` (`components/ApiUsageTitle.kt:13`) usa `profileLabel`; `CodexRepositoryImpl.getUsage()`
  (`:35-39`) não o preenche na conta padrão, então o card mostra só "Codex". Anthropic padrão:
  `AnthropicProfileRef.DEFAULT` com rótulo "Padrão" (`UsageTargetKey.kt:15`).
- `ModelPricingTable.kt` não tem nenhuma tarifa `gpt-*`: custo e "$ por mensagem" não são calculáveis.
- A janela Codex ignora a conta do card: `AppShellActionsFactory.kt:188` descarta o `UsageTargetKey`; lê sempre a
  conta padrão.

## Decisões

- **Sem valor novo em enum:** o Codex passa a usar `CliSessionRange` (que já tem `LAST_30D`) e
  `CodexCliSessionRange` é removido. O corte continua corrido; `CliSessionRange.resolve` sem janelas de quota.
- Rótulos de período, exportação e rodapé reaproveitam os do Anthropic, não cópia.
- Tempo ativo do Codex pela mesma regra de `activeTimeMillisOf` (intervalos entre respostas < corte), sobre `ts`.
  `null` = não medido; zero = medido.
- Título da conta padrão do Codex: "Padrão", como o Anthropic; janela "Codex CLI Sessions — Padrão".
- Mistura de modelos continua "% dos tokens" (sem tarifa).

## Atividades

| Atividade | O que faz | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Codex usa `CliSessionRange` (5h/7 dias/30 dias/Total) e rótulos de exportação do Anthropic | frontend-dev |
| A03 | Tempo ativo por sessão Codex (`activeMillis` no resumo, regra de `activeTimeMillisOf`) | backend-dev |
| A04 | Resumo e linha: tile de tempo ativo, linha de tokens, coluna de tempo ativo, copiar id, rodapé "last change" | frontend-dev |
| A05 | Títulos: conta padrão do Codex rotulada "Padrão"; janela "Codex CLI Sessions — Padrão"; protótipo e design system | frontend-dev |

## Ponto de situação

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos e escopo aprovado | `gh issue view 558`; greps listados em "Fatos confirmados"; escopo escolhido pelo dono no chat |

## Problemas em aberto e riscos

- **Custo e linha de saúde** fora: exigem tarifa oficial de `gpt-6.1-sol`, `gpt-6.1-terra`, `gpt-6-luna` e paridade
  com o servidor (`tools/ci/check-pricing-parity.mjs`).
- **"unknown model" de 61-80%** nas capturas: respostas sem `model` no índice. Causa não investigada (hipótese:
  rollout sem `turn_context.model` antes da resposta).
- **Janela por conta:** contas Codex extras abrem as sessões da conta padrão; exige índice por `CODEX_HOME`.
- `profileLabel` "Padrão" no Codex muda também texto de alerta, cache do dashboard e snapshot (web/Telegram), que
  passam a dizer "Codex — Padrão", como já fazem com o Anthropic.
