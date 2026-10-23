# Modelos por sessão CLI — issue #445

## Objetivo

O usuário troca de modelo várias vezes numa mesma sessão do Claude Code (e do Codex) e quer ver **quais modelos a sessão
usou e quanto cada um pesou**, em porcentagem. A conversa citada na issue (X, @i_mika_el) diz o mesmo: sessões parecidas
consomem a janela de 5 h de jeitos diferentes, e "modelo mais cache lido/gravado por sessão" é o recorte que explica
o pico. Hoje a linha da sessão não mostra modelo nenhum, o detalhe mostra o modelo só turno a turno e o modo terminal
mostra só o modelo predominante. A mudança vale **no app e no modo terminal (TUI)**.

## Fatos confirmados (antes de qualquer código)

- **O dado já está no índice.** `cli_turns.model` guarda o modelo de cada turno; `SELECT_SESSIONS_SINCE_SQL` e
  `SELECT_USAGE_GROUPS_SQL` já agrupam por `(sessão, modelo)`. Não precisa de coluna nova nem de releitura
  (`INDEX_SCHEMA_VERSION` fica onde está).
- **O corte por modelo já existe para a janela inteira**: `toUsageBreakdown().byModel` (`CliUsageBreakdown.kt`) dobra
  linhas `(sessão, modelo)` em `CliUsageBucket`, com custo recalculado pela `ModelPricingTable`, turnos sem tarifa em
  `unpricedTurnCount` e ordem total (`rankedByCost`). A aba Resumo usa "Fatia" = fatia do **custo**.
- **Leitura com janela** (5 h, 7 d, 30 d) passa pelo `WindowedSessionAccumulator`, que já recebe um grupo por modelo e
  só guarda o predominante (`primaryModel`). O time usa o mesmo acumulador (`TeamUsageMapper`).
- **Leitura sem janela** ("Total") vem de `cli_sessions`, que não tem o corte por modelo.
- **O detalhe** carrega os turnos (`CliSessionDetail.turns`), cada um com modelo e tokens.
- **Codex**: o resumo é montado dos turnos em memória (`LocalCodexCliSessionDataSource.buildSummary`), cada turno com
  `model`; o `primaryModel` é o do primeiro turno. O Codex não tem tarifa no app.
- **TUI, aba 2**: `TuiSession.model` é o `primaryModel`; o bloco da selecionada tem título, composição e contexto
  (`TuiSessionsView.DETAIL_ROWS = 5`).
- `LocalCliSessionDataSource.kt` tem 730 linhas (teto 800): leitura nova sai em arquivo próprio.

## Decisões

- **D1 · Base da porcentagem.** Fatia do **custo** a preço de tabela, a mesma "Fatia" da aba Resumo: é o que aproxima
  o peso de cada modelo na cota (o mesmo volume de tokens pesa muito mais no Opus que no Haiku). Quando a sessão não
  tem nenhum custo calculado (Codex, ou só modelos sem tarifa), a base passa a ser **tokens**, e o rótulo diz qual
  base está em uso ("% do custo" / "% dos tokens"). Modelo sem tarifa numa sessão com base custo mostra "—" na fatia,
  nunca 0% (custo não medido não é custo zero). Função pura no domain (`sessionModelMixOf`).
- **D2 · Um tipo só para os dois CLIs.** O corte por modelo de cada sessão é `List<CliUsageBucket>` (rótulo = modelo),
  o mesmo tipo do Resumo. O Codex é convertido como a TUI já faz: entrada cobrável, saída, `cached` = cache lido,
  `cache_write` = cache gravado, sem tarifa.
- **D3 · Campo novo com default.** `CliSessionSummary.modelBreakdown` e `CodexCliSessionSummary.modelBreakdown`,
  vazios por padrão (vazio = não lido). Nenhum valor novo em enum existente.
- **D4 · Onde o dado nasce.** Com janela: o `WindowedSessionAccumulator` guarda os grupos e dobra pelo mesmo
  `toUsageBreakdown().byModel` (o time ganha junto). Sem janela: uma consulta `(sessão, modelo)` desde 0, em arquivo
  próprio (`LocalCliSessionModelMix.kt`). Detalhe: dos turnos (`CliSessionAnalytics.modelBreakdown`), caindo para o do
  resumo quando o time não mandou turnos.
- **D5 · App.** Linha da sessão (Claude): terceira linha com a mistura, `claude-opus-4-1 82% · claude-haiku-4-5 18%`
  (a linha não mostra modelo hoje; como coluna estouraria o orçamento de 960dp). Linha do Codex: a mistura entra na
  segunda linha quando há mais de um modelo. Detalhe (Claude e Codex): seção "Modelos da sessão", visível (não no
  Avançado), tabela Modelo · Turnos · Tokens · Cache lido · Cache gravado · Custo · Fatia (% + trilha).
- **D6 · Modo terminal.** Coluna MODELO mostra o predominante pela fatia e `+N` quando houve troca; o bloco da
  selecionada ganha a linha `modelos   claude-opus-4-1 82% · claude-haiku-4-5 18%` (com a base escrita), e
  `DETAIL_ROWS` passa a 6. Sem cor, a linha é só texto; ASCII sem glifo novo.
- **D7 · Exportação e PDF ficam como estão** nesta issue: CSV/JSON são contrato e o PDF tem layout próprio. Fica
  registrado como próximo passo.
- **D8 · Textos** por objetos de rótulos (`CliSessionsLabels`, `TuiSessionsLabels`), nunca `== AppLanguage.PT` inline.

## Atividades

| Item | O quê |
| --- | --- |
| A01 | Este plano e a issue #445 com ele em comentário |
| A02 | Domain e data: `sessionModelMixOf`, `modelBreakdown` no resumo do Claude (com e sem janela, time) e do Codex, `CliSessionAnalytics.modelBreakdown`; testes |
| A03 | App: mistura na linha (Claude e Codex) e seção "Modelos da sessão" no detalhe (Claude e Codex); testes de tela; protótipo (§6, §7), kit (`CliSessions.jsx`, `SessionDetail.jsx`) e `docs/presentation.md` |
| A04 | Modo terminal: `+N` na coluna MODELO e linha `modelos` no bloco da selecionada; testes; `docs/design-system/cli/readme.md` e protótipo 3.2 |
| A05 | Testes locais completos, relatório da skill `usage-monitor-test-report`, PR e CI |

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
| --- | --- | --- | --- | --- |
| A01 | 2026-10-23 | `docs(cli-sessions): plan per-session model mix (#445)` | concluída | Worktree `../usage-monitor-445`, branch `feat/session-model-mix-445` a partir da `main` (`f97e1fc4`). Levantamento: `LocalCliSessionIndexSql.kt` (`cli_turns.model`, agrupamentos por `(sessão, modelo)`), `WindowedSessionAccumulator`, `CliUsageBreakdown.toUsageBreakdown`, `LocalCodexCliSessionDataSource.buildSummary`, `TuiSessionsView`/`TuiSessionsReader`, `CliSessionRow`/`CliSessionDetail`. Só documentação; nenhum teste rodado |
| A02 | 2026-10-23 | `feat(cli-sessions): split each session by model (#445)` | concluída | `CliSessionModelMix.kt` (domain): `sessionModelMixOf` (base custo; sem custo, tokens; sem tarifa = fatia `null`), `modelBreakdownBySession`, `toModelBreakdown` (turnos, subagente incluso), `toCodexModelBreakdown`. `modelBreakdown` com default em `CliSessionSummary`, `CodexCliSessionSummary` e `CliSessionAnalytics` (também no `fromSummary`, para o detalhe do time sem turnos). Com janela: `WindowedSessionAccumulator` guarda os grupos (time junto). Sem janela: `readUsageGroupRows` em `LocalCliSessionUsageGroups.kt`, que o `readUsageGroups` passou a usar (`LocalCliSessionDataSource.kt` 730 → 713 linhas). `JAVA_HOME=~/.jdks/ms-21.0.12.1 bash ./gradlew desktopTest -Dorg.gradle.java.home=$JAVA_HOME --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.presentation.*"`: verde; `CliSessionModelMixTest` 9/9 e o teste novo do `LocalCliSessionDataSourceTest` (o corte com e sem janela é o mesmo) |
| A03 | 2026-10-23 | `feat(cli-sessions): show the model mix in the session list and detail (#445)` | concluída | `SessionModelMixViews.kt`: `SessionModelMixLine` (terceira linha da sessão do Claude; segunda do Codex só com troca de modelo) e `SessionModelTable` ("Modelos da sessão": Modelo · Turnos · Tokens · Cache lido · Cache gravado · Custo · fatia com trilha; Codex sem Custo), textos em `SessionModelMixLabels` por `localized`. Tabela fora do Avançado nos dois detalhes. Fixtures das capturas: a sessão saturada ganhou 3 subagentes no Haiku, com tokens e custo derivados dos turnos (o custo do Opus continua US$ 5,4792); o Codex troca para `gpt-6.1-sol-mini` nas últimas respostas. Coluna de tokens a 136dp (em 96 e 120 o cabeçalho cortava, visto em `generateScreenshots`). `img/cli-sessions.png` e `img/cli-session-detail.png` regeneradas; protótipo §6/§7, kit (`CliSessions.jsx`, `SessionDetail.jsx`) e `presentation.md`. `bash ./gradlew desktopTest -Dorg.gradle.java.home=… --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.screenshots.*"`: verde (4 testes novos de tela; 3 antigos ajustados porque a tabela repete "Custo" e "Tokens (com cache)" e empurra o Avançado para baixo da dobra) |
| A04 | 2026-10-23 | `feat(cli): split the selected session by model in the TUI (#445)` | concluída | `TuiSession.modelMix` (o `TuiSessionsReader` passa o corte do Claude e do Codex); coluna MODELO com o que mais pesou e `+N` quando houve troca; no bloco da selecionada, um modelo por linha (até 3, depois `+N modelos`): nome na cor da fonte, fatia (ou `sem tarifa`), barra de 16 células pela `quotaBar` e `lido N · gravado N`; legenda `modelos` e, na segunda linha, a base (`% do custo`/`% tokens`). **Achado na TUI real:** a nota de rodapé "sem tarifa" usava o modelo da linha e, numa sessão Opus + subagentes Haiku, apontava o Opus (que tem tarifa); passou a listar os modelos sem tarifa pelo corte. Textos em `TuiSessionsLabels`; `cli/readme.md` ("Modelos da sessão") e protótipo 3.2. `bash ./gradlew desktopTest -Dorg.gradle.java.home=… --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*"`: verde (3 testes novos, quadro exato de 70 a 200 colunas com as linhas de modelo). Conferido com `createDistributable` no tmux, dados desta máquina: 110×30 truecolor (sessão `opus-5-5 +1` com `claude-haiku-5-5 sem tarifa · lido 5,8M`) e 80×24 `NO_COLOR=1 LC_ALL=C` (barra de colchetes, separador `-`, sem glifo fora da tabela). Visto de passagem: `claude-haiku-5-5` não está na `ModelPricingTable` — fica como próximo passo, fora desta issue |
| A05 | 2026-10-23 | `docs(cli-sessions): record the local test run for #445` | concluída | `bash ./gradlew desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport -Pcoverage` (JDK 21 por `-Dorg.gradle.java.home`), `cd server && npm ci && npm run test:coverage` e `node .claude/skills/usage-monitor-test-report/scripts/build-test-report.mjs`: veredito **Pronto**, 3 554 testes (Kotlin 3 255, servidor 299), 0 falhas, 17 regras de arquitetura, todos os `@Test` declarados rodaram; cobertura de linhas total 83,3% (Kotlin 83,1%, servidor 92,7%). Testado no Linux (Bazzite, X11/Wayland não exercitados: nenhum host de janela mudou) |
