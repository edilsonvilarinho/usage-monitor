# Issues #381, #382, #384, #385, #386, #387, #388 — execução em branch única

## Ponto de situação

**Estado atual:** `Fase 0 — plano registrado (A01). Próximo: medições A02/A03 e galeria visual A04.`
**Última atualização:** 2026-10-06
**Branch:** `feat/issues-381-388`

### ▶ Atividade corrente
A02 — medição de timing de turnos (Claude e Codex) e do "não atualiza" do modal Codex.

### ⏭ Próxima atividade
A03 — medição de firewall do listener LAN (#388) e viabilidade Telegram (#387).

- A #383 está fechada (entregue em `e2e4ac88`, #390) e fica fora.
- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Registro de execução

| # | commit | Atividade | O que mudou | Evidência |
| --- | --- | --- | --- | --- |
| 1 | (este) | A01 | Plano registrado | Documento criado; nenhuma linha de produção alterada |

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
