# Contexto vivo indisponível sem turno real na thread principal — issue #694

Procedência: Claude Opus 5.5 (orquestração), implementação por `backend-dev` (A02) e `frontend-dev` (A03), Claude Code
CLI no Windows 11, 2026-11-27, branch `feat/issue-694-live-context-unavailable`, commits com a identidade
`claude <claude@anthropic.com>`.

## Objetivo

Sessão sem turno real na thread principal (só avisos `<synthetic>`, ou só subagente) não pode aparecer com contexto
vivo `0` e veredito "saudável". O contexto é **não medido**: tokens, saturação, custo da próxima mensagem e veredito
ficam indisponíveis na lista, no detalhe, na TUI e no pulso. Regra do `CLAUDE.md`: `null` é "não medido", zero é
"medido e sem valor".

## Fatos confirmados (2026-11-27, `964d1b5d`)

- `LocalCliSessionDataSource.kt:499,680`: `liveContextTokens = liveContext?.cacheReadTokens ?: 0L` — ausência de
  turno real vira `0`.
- `ComputeCliSessionAnalyticsUseCase.kt:38`: o detalhe faz o mesmo (`lastMainTurn?.cacheReadTokens ?: 0L`).
- `CliSessionContextStatus.kt:13,25`: `liveContextTokens: Long = 0L`; `health` usa `contextSaturation ?: 0.0` e cai
  em `HEALTHY`.
- `CliSessionModels.kt:111,290` e `WindowedSessionAccumulator.kt:27`: `liveContextTokens: Long = 0L`.
- `LocalCliSessionIndexSql.kt:539-564`: o contexto vivo é lido de `cli_turns` (último `seq` com `is_sidechain = 0` e
  modelo não sintético); `cli_sessions` não tem coluna de contexto — sem migração de índice.
- `TuiSessionsReader.kt:89`: a TUI já esconde `0` (`takeIf { tokens > 0L }`), confundindo medido-zero com não medido.
- `UsageExporter.kt:128,246`: exportação CSV/JSON escreve `live_context_tokens` como `Long`.
- `TeamUsageMapper.kt:66` envia `liveContextTokens` ao servidor; `TeamDto.kt:29,110,169` é `Long = 0L`.
- `UsageSnapshot.kt:10-21` não expõe sessão nem contexto (só contas e `lastCollectedAt`): nada a mudar ali.
- Consumidores do veredito: `SessionPulse.kt:103`, `CliSessionModels.kt:408` (`worstHealth`),
  `TeamUsageModels.kt:134,138`, `TeamPresence.kt:95`, `UsageAlert.kt:505`, `HudSessionSignals.kt:46`,
  `CliSessionRow.kt:238,337`, `CliSessionDetail.kt:279,385,392,479`, `CliSessionsFormatting.kt:406-476`.

Comandos: `Grep liveContextTokens src/`, varredura do `explorer` sobre `CliSessionHealth`/`.health`/`contextStatus`.

## Decisões

- `liveContextTokens: Long?` (default `null`) da leitura à tela; `0` continua válido (turno real sem `cache_read`).
- **Sem valor novo em `CliSessionHealth`** (regra do `CLAUDE.md`): `CliSessionContextStatus.health` passa a
  `CliSessionHealth?`, `null` só quando os tokens são `null`; custo da próxima mensagem `Long?` pela mesma regra.
- Sessão não medida **não alerta**, não entra em `worstHealth` nem na contagem por veredito (pulso, HUD, time, alerta).
- Protocolo do time inalterado: o envio manda `null` como `0` (`TeamDto` segue `Long`); servidor antigo e novo
  continuam compatíveis. Sessão vinda do servidor é sempre medida.
- Exportação: JSON `null`, célula CSV vazia — o arquivo diz "não medido" em vez de afirmar zero.
- Tela: reusa primitivas existentes (`AppStatusIndicator`/`AppBanner` com `AppTone.NEUTRAL`, "—" nos cartões),
  sem direção visual nova; por isso sem rodada de opções. Registro no protótipo e no design system no commit de A03.
- Dois commits que compilam sozinhos: A02 separa "não medido" nos dados mantendo o comportamento visível; A03 tira
  o veredito e mostra o estado.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | `liveContextTokens: Long?` em resumo, status, acumulador, fonte local, TUI, exportação e envio do time + testes |
| A03 | Veredito e custo anuláveis, análise do detalhe, consumidores (pulso, HUD, time, alerta), lista e detalhe mostram "indisponível" + protótipo + design system + cena de captura |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `git log -1` | plano commitado (`3f7adc63`) |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest"` (`backend-dev`) | `LocalCliSessionDataSourceTest.kt:543` (só subagente) e `:562` (só `<synthetic>`): `AssertionError`, esperado `null`, era `0`; 67 testes, 2 falhas |
| A02 | vermelho por reversão temporária da linha de produção (`backend-dev`) | `CliSessionAnalyticsTest.kt:27,34`: `expected null, but was:<0.0>`; `UsageExporterTest.kt:32,39`: `Expected value to be true.`; `TuiSessionsReaderTest.kt:36`: `expected null, but was:<0>`; zero medido: `expected:<0> but was:<null>` |
| A02 | verde: `gradlew.bat desktopTest --rerun --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.terminal.TuiSessionsReaderTest" --tests "*ArchitectureRulesTest*"` (`test-runner`) | exit 0; 1400 testes, 0 falhas, 0 erros. Os 2 casos novos de `CliSessionContextStatusTest` não tiveram vermelho próprio (escritos junto do código) |
| A03 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.CliSessionContextStatusTest"` (`frontend-dev`) | `CliSessionContextStatusTest.kt:149,195`: `AssertionError` (veredito `null` esperado) |
| A03 | vermelho de tela: testes novos de `CliSessionsScreenTest` contra a produção de `3f903e3c` em worktree temporário (`test-runner`) | 58 testes, 2 falhas: `:975` e `:1002` `could not find any node that satisfies: (Text … contains 'Contexto indisponível'` |
| A03 | vermelho do pulso: `SessionPulseTest` com a guarda de `SessionPulse.kt:103` revertida | só fica vermelho revertendo também o filtro `withKnownWindow()` (`CliSessionModels.kt:395`), que já descarta saturação `null`; guarda mantida por exigência do tipo, teste fica como regressão |
| A03 | verde: `gradlew.bat allTests --rerun-tasks` (`test-runner`) | exit 0; 4919 testes, 0 falhas, 1 ignorado. A 1ª rodada (sem `--rerun-tasks`) deu 2 falhas — `LocalCliSessionDataSourceTest.kt:555` esperava `HEALTHY` e o matcher de "—" em `CliSessionsScreenTest.kt:1002` não achava nó —, corrigidas no teste |
| A03 | protótipo e design system (`prototype-dev`), sem harness | `grep -c "Contexto indisponível"`: protótipo 5 (seção 6 `:1662-1667`, seção 7 `:1922-1946`), `docs/design-system/readme.md` 1, `ui_kits/desktop-app/CliSessions.jsx` 2; balanço de tags inalterado |

## Problemas em aberto e riscos

- Servidor de time recebe `0` para sessão não medida e a exibe como medida na tela do time; levar o `null` ao
  protocolo exige mudar DTO e esquema do servidor — fora desta entrega.
- Contagem de turnos `<synthetic>` segue a decisão da #690.
