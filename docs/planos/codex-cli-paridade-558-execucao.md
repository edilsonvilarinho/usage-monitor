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
- Copiar da linha Codex copia `codex resume <id>`, como o Anthropic copia o comando de retomada (`codex resume --help`,
  CLI local: `Usage: codex resume [OPTIONS] [SESSION_ID] [PROMPT]`, aceita UUID). A04 foi dividida: copiar e rodapé
  exigem `CopySessionCommandButton` e `lastChangedAt`, fora da lista de arquivos da A04 → A05.

## Atividades

| Atividade | O que faz | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Codex usa `CliSessionRange` (5h/7 dias/30 dias/Total) e rótulos de exportação do Anthropic | frontend-dev |
| A03 | Tempo ativo por sessão Codex (`activeMillis` no resumo, regra de `activeTimeMillisOf`) | backend-dev |
| A04 | Resumo e linha: tile de tempo ativo, linha de tokens, coluna de tempo ativo | frontend-dev |
| A05 | Botão de copiar `codex resume <id>` na linha; rodapé "última alteração" (`lastChangedAt` no view model) | frontend-dev |
| A06 | Títulos: conta padrão do Codex rotulada "Padrão"; janela "Codex CLI Sessions — Padrão"; protótipo | frontend-dev |

## Ponto de situação

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos e escopo aprovado | `gh issue view 558`; greps listados em "Fatos confirmados"; escopo escolhido pelo dono no chat |
| A02 | Codex usa `CliSessionRange` (5h/7 dias/30 dias/Total, corte de 30 dias no view model, slug `30d`); botões "Exportar CSV/JSON" de `ExportLabels`; `CodexCliSessionRange` removido; teto `INLINE_LANGUAGE_CHECKS_CEILING` 700 → 699; nota no protótipo | Vermelho: `CodexCliSessionsViewModelTest` → `expected:<1786287600000> but was:<null>` (stub `LAST_30D -> null`); `CodexCliSessionsScreenTest.kt:99` → `Expected exactly '1' node ... contains '7 dias'`; `:113` → `expected:<ALL> but was:<LAST_30D>`; `UsageExportRequestsTest.kt:48` → 3 slugs esperados, 4 reais. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.CodexCliSessionsScreenTest"` → 1137 testes, 0 falhas |
| A03 | `activeMillisOfTimestamps` (domain) é a regra única de tempo ativo; `activeTimeMillisOf` delega a ela; `CodexCliSessionSummary.activeMillis` (`null` = não medido) preenchido em `buildSummary` a partir do `ts` das respostas do período | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCodexCliSessionDataSourceTest" --tests "com.usagemonitor.domain.CliSessionActiveTimeTest"` → `LocalCodexCliSessionDataSourceTest.kt:171` `expected:<120000> but was:<null>`. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.domain.*"` → 1262 testes, 0 falhas |
| A04 | Resumo do Codex: tile "Tempo ativo" (soma de `activeMillis`, "—" sem medida), linha `in · out · cache lido · cache gravado` por `CliSessionsLabels.tokensBreakdown` (`in` = input − cache lido); coluna "Tempo ativo" (76dp; id 140→130, projeto 150→140); nota no protótipo | Vermelho: `CodexCliSessionsScreenTest` 20 testes, 4 falhas → `Expected exactly '1' node ... contains 'in 2K · out 2,5K · cache lido 8K · cache gravado 500'`; `contains '2min'`; `contains '1h07'`; `Expected '3' nodes but found '2' ... '—'`. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.CodexCliSessionsScreenTest" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.presentation.*"` → 1141 testes, 0 falhas |
| A05 | `CopySessionCommandButton` ganha `command` (padrão `claude --resume`); linha do Codex copia `codexResumeSessionCommand` = `codex resume <id>`; `lastChangedAt` no `CodexCliSessionsUiState.Success`, anda só quando lista ou aviso do índice muda; rodapé "última alteração"; teto `INLINE_LANGUAGE_CHECKS_CEILING` 699 → 698. Devolvida 1×: o teste de cópia não afirmava o comando copiado | Vermelho: `CodexCliSessionsViewModelTest.kt:235` → `expected:<2026-09-08T12:00:00Z> but was:<null>`; `CodexCliSessionsScreenTest.kt:54/72` → `assertExists ... 'última alteração'`/`'last change'`; `:103` → botão de cópia ausente; `CopySessionCommandButtonTest.kt:47` → `expected:<[codex resume-RED f66da412…]> but was:<[codex resume f66da412…]>`. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.presentation.*"` → 1796 testes, 0 falhas |
| A06 | `CodexProfileRef.DEFAULT_LABEL = "Padrão"`; `CodexRepositoryImpl.getUsage()` (conta padrão) publica `profileLabel` → card/HUD "Codex — Padrão"; janela "Sessões Codex CLI — Padrão" / "Codex CLI Sessions — Padrão" por `codexCliSessionsWindowTitle`; titlebar e nota no protótipo. Grep: nenhum consumidor trata `profileLabel` nulo como conta padrão (todos `?: apiName`) | Vermelho: `CodexRepositoryImplTest.kt:267` → `expected:<Padrão> but was:<null>`; `CliSessionsLabelsTest.kt:34` → `expected:<Codex CLI [Sessions — Padrão]> but was:<Codex CLI [sessions]>`. Verde: `gradlew.bat allTests` → 4181 testes, 1 ignorado, 0 falhas |

## Problemas em aberto e riscos

- **Custo e linha de saúde** fora: exigem tarifa oficial de `gpt-6.1-sol`, `gpt-6.1-terra`, `gpt-6-luna` e paridade
  com o servidor (`tools/ci/check-pricing-parity.mjs`).
- **"unknown model" de 61-80%** nas capturas: respostas sem `model` no índice. Causa não investigada (hipótese:
  rollout sem `turn_context.model` antes da resposta).
- **Janela por conta:** contas Codex extras abrem as sessões da conta padrão; exige índice por `CODEX_HOME`.
- Cache do dashboard gravado antes da A06 tem `profileLabel` nulo: o card diz "Codex" até a próxima leitura (não medido).
- `profileLabel` "Padrão" no Codex muda também texto de alerta, cache do dashboard e snapshot (web/Telegram), que
  passam a dizer "Codex — Padrão", como já fazem com o Anthropic.
