# Issues #381, #382, #384, #385, #386, #387, #388 — execução em branch única

## Ponto de situação

**Estado atual:** `Direções escolhidas; Fase 2 em execução.`

**Direções escolhidas (2026-10-06):** #382 → N6 Degraus de consumo · #384 → O1 Evolução mínima · #386 → P7 Mapa de calor · #387 → Q10 Configuração e conversa · #388 → R3 Grade de anéis.
**Última atualização:** 2026-10-06
**Branch:** `feat/issues-381-388`

### ▶ Atividade corrente
A16 — modelo de comparação entre fontes (#386).

### ⏭ Próxima atividade
A17 — janela de comparação (P7 mapa de calor).

- A #383 está fechada (entregue em `e2e4ac88`, #390) e fica fora.
- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Registro de execução

| # | commit | Atividade | O que mudou | Evidência |
| --- | --- | --- | --- | --- |
| 1 | `1c90d4ad` | A01 | Plano registrado | Documento criado; nenhuma linha de produção alterada |
| 2 | `70ea48df` | A02 | Medição de timing Claude/Codex e do modal Codex | Scripts read-only em scratchpad sobre transcripts e índice reais; números na seção "Medições" |
| 3 | `70ea48df` | A03 | Viabilidade Telegram; firewall adiado para A21 | Bot API e FAQ oficiais consultados; ver "Medições" |
| 4 | `953db4c1` | A04 | Galeria única em `docs/planos/issues-381-388-visual/` (rodadas N–R, 10 opções por issue) | `node docs/planos/issues-381-388-visual/build-gallery.cjs` → 87,4 KB; aberta no browser pane: console sem erro, nenhum palco com transbordo horizontal nas 5 abas |
| 5 | `6a2abef7` | A05 | Escrita de turnos extraída para `LocalCliSessionTurnWriter.kt` (678 + 99 linhas), sem mudança de comportamento | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest" --tests "com.usagemonitor.architecture.*"` → 57 + 8 testes, 0 falhas |
| 6 | `aa04c4c3` | A06 | `cli_turns` ganha `request_ts`/`last_line_ts`; conflito do `message_id` funde por `MAX` (corrige subcontagem de saída de 5,64%); `INDEX_SCHEMA_VERSION` 3; `OutputThroughput` no domain e `CliSessionSummary.throughput` | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest" --tests "com.usagemonitor.domain.OutputThroughputTest" --tests "com.usagemonitor.architecture.*"` → 62 + 4 + 8 testes, 0 falhas (5 testes novos de índice, 4 de domínio) |
| 7 | `70533949` | A07 | Parser do Codex lê o envelope de `turn_context`/saída de ferramenta/mensagem do usuário como início do pedido; `codex_cli_turns.request_ts`; `codex_cli_index_meta` versão 1 relê os rollouts; `CodexCliSessionSummary.throughput`; `measuredThroughput` no domain | `gradlew.bat desktopTest --tests "com.usagemonitor.data.CodexCliRolloutParserTest" --tests "com.usagemonitor.data.LocalCodexCliSessionDataSourceTest" --tests "com.usagemonitor.domain.*"` → 5 + 4 + domínio, 0 falhas (3 testes novos) |
| 8 | `efe35630` | A09 | `usageProgressed`/`recentlyProgressed` no domain; `QuotaActivityTracker` publica alvos com consumo avançando (10 min); HUD usa `hudActiveTargets` = CLI ∪ detectado; `cliBusy` intocado | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.QuotaUsageProgressTest" --tests "com.usagemonitor.presentation.QuotaActivityTrackerTest" --tests "com.usagemonitor.presentation.SessionPulseViewModelTest" --tests "com.usagemonitor.architecture.*"` → 7 + 3 + 8 novos/arquitetura, 0 falhas |
| 9 | `cf8ab48d` | A10–A12 | Faixa ativa por janela (domínio, gráfico com fundo e chave, legenda, coluna Ativa na tela e no PDF); OpenCode Go e Codex mensal num card só com a cota mensal em "Todas"; protótipo §5, kit `History.jsx` e `presentation.md` atualizados. Cruzamento com turnos CLI descartado (justificativa em `presentation.md`) | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.QuotaWindowAnalysisTest" --tests "com.usagemonitor.presentation.History*" --tests "com.usagemonitor.ui.History*" --tests "com.usagemonitor.architecture.*"` → 0 falhas (5 testes de faixa, 3 de agrupamento); `gradlew.bat generateScreenshots -PscreenshotScenario=history-baseline` → faixa e legenda conferidas em `history-anthropic-dark-1030.png` |
| 10 | (este) | A13–A15 | Coluna e bloco Vazão nos modais Anthropic e Codex; Codex com abas Sessões/Resumo, métricas, PDF (`reportForCodexCliSessions`) e sem botão de atualizar (laço chama `refresh(showProgress = false)`); `reportRequest` leva o idioma (CLI e time). A13 não virou correção de dados: o defeito medido era de tela | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.CodexCliReportTest" --tests "com.usagemonitor.presentation.CodexCliSessionsViewModelTest" --tests "com.usagemonitor.presentation.CliSessionsViewModelTest" --tests "com.usagemonitor.presentation.UsageReportBuildersTest" --tests "com.usagemonitor.presentation.TeamUsageViewModelTest" --tests "com.usagemonitor.ui.*Cli*" --tests "com.usagemonitor.architecture.*"` → 0 falhas (4 testes novos); `generateScreenshots -PscreenshotOutputDir=build/issue384-screenshots` → coluna e bloco conferidos em `cli-sessions.png` |

## Medições (Fase 0)

### A02 — Claude Code (40 transcripts mais recentes de `~/.claude/projects/**`)

- 1.910 `message.id` aparecem em mais de uma linha `assistant`; em 122 o `output_tokens` **cresce** entre a
  primeira e a última linha.
- Soma de `output_tokens` pela primeira linha = 1.968.230; pela maior = 2.085.792 → o índice atual
  (`INSERT OR IGNORE` em `LocalCliSessionIndexSql.kt:229`, sem dedup prévia no parser) **subconta saída em
  5,64%** nessa amostra. Input/cache não foram medidos aqui. Correção entra na A06: no conflito,
  `output_tokens = MAX(...)` e horário da última linha.
- `system/turn_duration` existe em só 41 linhas (é por turno do usuário, não por resposta) → não serve como
  duração de geração.
- Fórmula escolhida para tok/s: `output_tokens / (ts da última linha do message.id − ts da linha user/tool_result
  imediatamente anterior)`, só com saída > 50 tokens e intervalo > 0,5 s. Resultado: n = 2.557, mediana
  **95,3 tok/s** (p10 71,6; p90 123,1). Inclui latência até o primeiro token — é vazão ponta a ponta, rotular assim.
- Alternativa `(última − primeira linha do mesmo id)` dá mediana 227,5 tok/s, mas ignora o tempo até a primeira
  linha; descartada.

### A02 — Codex (60 rollouts mais recentes de 385 em `~/.codex/sessions/**`)

- Eventos: `token_usage_record` (2.570), `event_msg:token_count` (2.574), `task_started`/`task_complete`
  (123/121, com `started_at`), `turn_aborted` com `duration_ms`.
- Mesma fórmula: `output_tokens / (ts do token_usage_record − ts da última entrada: turn_context,
  *_call_output ou mensagem user)`. n = 2.619, sem entrada ausente; mediana **32,5 tok/s** (p10 19,6; p90 51,0).
- Precisa gravar o horário de início por resposta: o parser hoje ignora `response_item` e `event_msg`.

### A02 — "Codex não atualiza em tempo real" (#384)

- `CodexCliSessionsViewModel.openWindow()` já roda o laço de 5 s (`CLI_SESSION_LIVE_INTERVAL_MILLIS`), com
  `refresh()` seguido de `loadJob?.join()` — o laço não se cancela sozinho.
- Índice real (`~/.usage-monitor/codex-cli-history.db`, read-only): 32 sessões, 2.729 turnos, 0 sessões sem
  originator; ler todas as sessões turno a turno levou ~12 ms. Hipótese de lentidão **refutada**.
- `token_usage_record` sai ~0,5 s depois do `token_count` correspondente: o rollout é gravado durante o turno.
- Diferenças restantes entre os modais são de UI: o Codex tem botão manual "Atualizar" e mostra "Atualizando…"
  a cada tique; não tem aba de detalhamento nem PDF. **Não há defeito de dados provado.** A13 deixa de ser
  `fix(codex-cli)` e vira paridade de UI dentro da A14, salvo reprodução do usuário com passos.

### A03 — #388 firewall e #387 Telegram

- Firewall: o usuário escolheu bind na LAN (`0.0.0.0`), que é exatamente o caso que o KDoc de
  `FocusRequestChannel.kt` cita como gatilho do prompt. A medição exige alguém para responder ao diálogo do
  Windows; fica na verificação manual da A21. Mitigação: serviço desligado por padrão e texto da seção avisando.
- Telegram Bot API (core.telegram.org/bots/api e /bots/faq):
  - `getUpdates(offset, limit 1–100, timeout em s, allowed_updates)`; `timeout` > 0 é long polling; updates
    ficam no servidor até 24 h.
  - `getUpdates` não funciona com webhook configurado → no pareamento, chamar `deleteWebhook`.
  - Limites: ~1 msg/s por chat, 20 msg/min por grupo, ~30 msg/s global; excesso responde 429.
  - `chat_id` vem de `message.chat.id` no update → pareamento: o app mostra um código, o usuário manda
    `/start <código>` ao bot, o app grava esse `chat_id` como autorizado.
  - Tudo é HTTPS de saída: cabe no Ktor client atual, sem listener. **Viável.**

## Contexto

Sete issues abertas de melhoria (a #383 está **fechada**, entregue no commit `e2e4ac88`/#390, e fica fora).
Entrega numa branch só: `feat/issues-381-388`. Cinco delas mudam superfície visível e passam pela skill
`usage-monitor-visual-options` antes de qualquer Kotlin. Decisões do usuário já tomadas:

- Galeria visual: #384, #386, #387, #382 e #388 (10 opções cada, #382 e #388 inclusive).
- #388: acesso pela **rede local** (celular) → bind `0.0.0.0` + token obrigatório.
- #387: **Telegram primeiro**; Discord fica documentado como fase 2 (Gateway WebSocket).

O plano vira `docs/planos/issues-381-388-execucao.md` no primeiro commit, com tabela de ponto de
situação atualizada **no mesmo commit** de cada atividade (comando rodado + resultado).

## Fatos levantados no código (base do sequenciamento)

| Issue | Evidência | Consequência |
|---|---|---|
| #381 tok/s | `cli_turns`/`codex_cli_turns` guardam 1 `ts` por resposta + `output_tokens`; nenhum início/duração. `turn_duration.durationMs` existe no transcript Claude, mas o DTO (`ClaudeTranscriptDto.kt`) não lê. Codex: `CodexCliRolloutParser.kt` ignora `event_msg`. `INSERT OR IGNORE` por `message_id` guarda a **primeira** linha (output_tokens pode não ser o final — hipótese a medir). | Exige mudança de schema + `INDEX_SCHEMA_VERSION` 2→3 (Claude) e mecanismo de versão novo no Codex (não existe). `LocalCliSessionDataSource.kt` tem 769 linhas: **extrair antes de crescer**. |
| #385 indicador | Ativo só via CLI (`GetActiveCliSessionPulsesUseCase`, `LocalCodexActivityDataSource`). Nenhuma fonte compara leituras sucessivas. `busy` é global e acelera polling de todos. | Função pura nova no domain (delta de `used` entre snapshots), nova entrada em `_activeTargets` do `SessionPulseViewModel`. Não pode acelerar polling (senão realimenta: polling rápido → mais deltas). |
| #382 janelas | `usage_snapshots` só tem `period_end_at`; `QuotaWindowSummary.firstObservedAt/lastObservedAt` = primeiro/último **poll**, não atividade. Atividade real só em `cli_sessions.first_ts/last_ts` e `cli_turns.ts`. OpenCode Go: labels `Go 5h`/`Go semanal`/`Go mensal` não agrupam em `buildGenericHistoryGroups` (só tira sufixo ` 5h`/` 7d`) → 3 cards soltos, sem seletor. `HistoryQuotaView` = `INTERVAL/WEEKLY/BOTH`. | Faixa ativa = primeiro/último snapshot com **delta > 0** (reusa função da #385) + turns CLI quando a fonte é Anthropic/Codex. 30d do Go **sem valor novo em enum** (regra do CLAUDE.md): agrupamento por chave de grupo explícita + série mensal como sobreposição/linha própria. |
| #384 modais CLI | Ambos já fazem polling de 5 s (`CLI_SESSION_LIVE_INTERVAL_MILLIS`); Codex "não atualiza" é sintoma, causa não confirmada (hipóteses: rollout só grava `token_usage_record` tardio; `readSessions()` + `refreshMissingOriginators` lento + `refresh()` cancelando `loadJob` a cada tique). Anthropic já exporta PDF; Codex só CSV/JSON. `reportRequest(...)` não seta `Report.language` → rodapé cai no fallback PT. | Diagnóstico medido antes de corrigir; PDF Codex via `UsageReportDocument`; corrigir idioma do PDF. |
| #386 comparativo | Nenhuma tela compara fontes; só breakdown por modelo dentro de cada CLI. `ModelPricingTable` só Claude. | Tela nova (modal `AppDialogWindow`) consumindo `CliUsageBreakdown.byModel` (Claude+Codex), cotas (`ApiUsageStats`) e tok/s da #381. Codex sem tarifa → `unpricedTurnCount`, nunca custo zero. |
| #387 bot | Nenhum servidor; `UsageAlertViewModel.alerts: SharedFlow<UsageAlert>` (multi-coletor) + `usageAlertMessage` reutilizáveis. Ciclo de vida a copiar: `TeamSyncService`. Segredo: padrão `LocalTeamSettingsDataSource` (`~/.usage-monitor/team.json`, `restrictToOwnerReadWrite`). | Telegram por long polling (`getUpdates`), só saída HTTPS via Ktor client existente (`HttpClientFactory.kt`, com proxy). |
| #388 web local | Nenhum listener; `FocusRequestChannel.kt` evitou socket por prompt de firewall. `HudAccount`/`HudQuota` não são `@Serializable` e carregam tipos Compose. `jlink` declara só `java.sql`, `java.logging` (`build.gradle.kts:146`). | Servidor `com.sun.net.httpserver` (zero dependência) exige `jdk.httpserver` no `modules(...)` em **todas** as plataformas. Snapshot JSON por porta nova no domain (precedente `UsageExportEncoder`). Bind LAN → prompt de firewall é esperado, mas só ao habilitar (opt-in). |

## Ordem e justificativa

Dependências: #381 alimenta #384 e #386; #385 alimenta #382 e o snapshot de #388/#387; snapshot da #388
é reutilizado pela #387 (comando `/status`). Visual é decidido de uma vez para não bloquear no meio.

```
Fase 0  Plano + estudos medidos (sem produção)          A01–A03
Fase 1  Galeria visual única (5 issues × 10 opções)     A04  → ESCOLHA DO USUÁRIO (único ponto de parada)
Fase 2  Fundações de dados                               A05–A09  (#381, #385)
Fase 3  Telas existentes                                 A10–A15  (#382, #384)
Fase 4  Tela nova                                        A16–A18  (#386)
Fase 5  Integrações externas                             A19–A25  (#388, #387)
Fase 6  Fechamento                                       A26
```

Fases 0 e 2 rodam enquanto o usuário avalia a galeria (não dependem da escolha visual).

## Atividades (1 atividade = 1 commit, Conventional Commits em inglês)

### Fase 0 — plano e medições
- **A01** `docs: add execution plan for issues 381-388` — `docs/planos/issues-381-388-execucao.md` com este conteúdo + tabela de situação.
- **A02** Medição #381/#384 (doc only): script em scratchpad lê transcripts reais `~/.claude/projects/**` e rollouts `$CODEX_HOME/sessions/**`; registra no plano: (a) linhas com mesmo `message.id` têm `output_tokens` crescente?; (b) presença de `turn_duration.durationMs` e timestamps de `user` precedente; (c) no Codex, quais tipos de evento carregam tokens/tempo (`token_count`, `task_started`/`task_complete`); (d) tempo de um `refresh()` Codex com o índice real. Decide a fórmula de tok/s e a causa do "não atualiza".
- **A03** Medição #388: subir `HttpServer` mínimo num teste manual em `127.0.0.1` e em `0.0.0.0`, registrar se o Firewall do Windows pergunta em cada caso (corrige ou confirma o KDoc do `FocusRequestChannel`). Estudo de viabilidade #387 (Bot API Telegram: `getUpdates` long polling, `sendMessage`, limites 30 msg/s, `allowed chat_id`) no plano.

### Fase 1 — galeria visual (skill `usage-monitor-visual-options`)
- **A04** Gerar 5 páginas `build/gargantua-preview/issue-<n>-options.html` (10 opções cada, letras de rodada N–R, sem repetir rejeitadas do histórico da skill) + **índice** `build/gargantua-preview/issues-381-388-index.html` com abas por issue e o card "Hoje (referência)" de cada uma. Conteúdo real do app. Temas:
  - #382 faixa ativa da sessão em 5h/7d/30d (linha do tempo da janela, início/pico/fim de atividade, Go com 3 janelas).
  - #384 modais CLI Anthropic+Codex mesmo padrão (lista, detalhe, ao vivo, tok/s, export PDF).
  - #386 tela comparativa (modelos/APIs: custo, tokens, tok/s, % cota, período).
  - #387 seção "Bot" nas Configurações (pareamento, chat id, eventos, comandos, teste).
  - #388 página web da HUD (desktop e celular, detalhe por API) + seção "Acesso web" nas Configurações (porta, token, QR/URL).
  - Validar no browser pane, `show_widget` + `SendUserFile` do índice. Cópia aprovada vai para `docs/planos/issues-381-388-visual/` (precedente `issue383-visual/`).
  - **Parada:** usuário escolhe 1 direção por issue. Sem Kotlin visual antes disso.

### Fase 2 — fundações (#381, #385)
- **A05** `refactor(cli-index)`: extrair de `LocalCliSessionDataSource.kt` (769 linhas) o bloco de upsert de turnos para arquivo próprio. Sem mudança de comportamento; `allTests` verde.
- **A06** `feat(cli-index): record turn timing for Claude` — colunas novas em `cli_turns` (ex.: `started_ts`, `duration_ms` — nomes finais definidos pela medição A02), DTO lê os campos, `INDEX_SCHEMA_VERSION` 3 com quem preenche. `null` = não medido.
- **A07** `feat(codex-index): add schema version and turn timing` — tabela meta no `codex-cli-history.db` + parse dos eventos medidos em A02.
- **A08** `feat(domain): output token throughput` — função pura (tokens de saída / duração de geração, só turnos com duração medida; mediana por sessão/modelo), testes em `commonTest`.
- **A09** `feat(activity): infer activity from quota deltas` — função pura no domain (`used` subiu entre leituras consecutivas da mesma janela, ignorando reset via `hasQuotaResetSince`) → alvo ativo por N min em `SessionPulseViewModel`, **sem** alimentar `cliBusy` (evita realimentação de polling). Fontes: MiniMax, DeepSeek (saldo cai), OpenCode Go, Cursor, Antigravity. Doc em `docs/presentation.md`.

### Fase 3 — telas existentes (#382, #384)
- **A10** `feat(history): active session span per window` — `QuotaWindowSummary` ganha `firstActiveAt/lastActiveAt` (delta > 0, função da A09) + cruzamento com turns CLI para Anthropic/Codex; tabela e PDF do histórico mostram faixa ativa e duração.
- **A11** `feat(history): group OpenCode Go windows` — agrupamento 5h/semanal/mensal por chave explícita (sem renomear labels — quebram série; sem valor novo em `HistoryQuotaView`); 30d exibido conforme direção escolhida.
- **A12** Visual #382 conforme opção escolhida + protótipo §/design system no mesmo commit.
- **A13** `fix(codex-cli): live refresh` — correção da causa medida em A02 (não aplicar sem prova).
- **A14** `feat(cli): unified CLI modal layout` — direção escolhida da #384 nos dois modais, tok/s da A08, primitivas existentes (`AppStructure`/`AppTabs`/...), protótipo + `ui_kits` atualizados.
- **A15** `feat(cli): PDF export for Codex + report language` — `reportForCodexCliSessions` em `UsageReportBuilders.kt`; `reportRequest(...)` passa `language` (corrige rodapé PT em EN para CLI e time).

### Fase 4 — #386
- **A16** `feat(domain): cross-source comparison model` — use case puro agregando por modelo/fonte e período (tokens, custo recalculado por `ModelPricingTable`, `unpricedTurnCount`, tok/s, pico de cota). Ordem total determinística.
- **A17** `feat(compare): comparison window` — `ComparisonViewModel` em `AppViewModels`, `AppDialogWindow` em `ModalWindowsHost`, entrada no balão da engrenagem; visual da direção escolhida; protótipo nova seção `<h2>` + `nav.index`.
- **A18** Export PDF/CSV da comparação via `UsageReportDocument`.

### Fase 5 — #388 e #387
- **A19** `feat(domain): usage snapshot port` — porta `UsageSnapshotEncoder` no domain + DTO `@Serializable` em `data` (cotas, risco, sessão ativa, modelos observados; nunca prompt/resposta).
- **A20** `build: add jdk.httpserver to jlink modules` — `build.gradle.kts:146`, todas as plataformas.
- **A21** `feat(web): local HUD web server` — `LocalWebAccessService` (desktopMain, ciclo de vida do `TeamSyncService`), `HttpServer` opt-in, bind `0.0.0.0`, token aleatório 32 B em `~/.usage-monitor/web-access.json` (padrão `LocalTeamSettingsDataSource`), comparação em tempo constante, rotas `GET /` (HTML estático do recurso), `GET /api/snapshot` (JSON). Testes de rota com cliente real em loopback.
- **A22** `feat(settings): web access section` — `SettingsTab` **ganha valor novo**: viola "nenhum valor novo em enum existente" → alternativa: subseção dentro de `NETWORK` (decidir na direção visual escolhida; se precisar de aba, pedir exceção explícita ao usuário antes).
- **A23** `feat(telegram): bot client and settings storage` — `~/.usage-monitor/telegram.json` (token + chat ids autorizados), `TelegramBotService` com long polling, backoff em 429/`retry_after`, só aceita `chat_id` pareado (código de pareamento exibido no app).
- **A24** `feat(telegram): push alerts and commands` — coletor extra de `UsageAlertViewModel.alerts` com `usageAlertMessage(alert, language)`; comandos `/status` (usa snapshot A19), `/alerts on|off`, `/quiet HH-HH`, `/threshold N` (escreve nas mesmas preferências de alerta). Nada de prompt/resposta.
- **A25** Seção "Bot" (mesma restrição de enum da A22) + protótipo/design system.

### Fase 6
- **A26** Documentação final (`docs/integrations.md` Telegram, `docs/presentation.md`, `CLAUDE.md` regras novas mínimas), situação "Concluída", PR com plataformas testadas (#342: Windows; Linux X11 para hosts de janela novos — se não testado, PR declara o risco).

## Riscos e pontos de atenção

- Limites de tamanho (`ArchitectureRulesTest`): `DashboardViewModel`, `LocalCliSessionDataSource`, `SettingsDialogContent` perto/acima do teto → extrair antes.
- Regra "nenhum valor novo em enum existente" atinge `HistoryQuotaView` e `SettingsTab` — contornado por parâmetro/subseção; se a direção visual exigir aba nova, pergunto antes.
- #388 em LAN: exposição de metadados de uso na rede; token obrigatório, desligado por padrão, aviso de firewall no texto da seção.
- #387: token do bot é segredo — tipo sem `data class` (`toString` vaza, precedente `CursorSessionCredentials`).
- #381: se A02 mostrar que não há duração confiável no Codex, tok/s Codex fica `null` ("não medido"), não estimado.
- #385: delta de cota é inferência de 1–5 min de atraso (granularidade do polling); documentar como "uso detectado", não "sessão ativa".

## Verificação

- Por atividade: `gradlew.bat desktopTest --tests "<pacote tocado>"`; ao fim de cada fase `gradlew.bat allTests` (inclui `ArchitectureRulesTest`).
- Reindex: apagar índice local de teste e conferir `INDEX_SCHEMA_VERSION=3` em `cli_index_meta`.
- Visual: `gradlew.bat run`, abrir cada modal/aba com dados reais; capturas comparadas ao protótipo.
- #388: abrir `http://<ip-lan>:<porta>/?token=…` no celular; sem token → 401.
- #387: bot de teste do usuário (token digitado **pelo usuário** no app — não por mim), `/status` e alerta forçado.
- `server/`: sem mudança prevista (servidor não precifica; paridade de preço intocada).
