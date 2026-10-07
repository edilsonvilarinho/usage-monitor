# Issues #392 e #393 — histórico por janela e gráficos das sessões CLI

## Ponto de situação

**Estado atual:** `Plano aprovado (2026-10-06). Em execução.`

**Direções escolhidas (2026-10-06):** #392 → **S9 · Lista de janelas e detalhe** · #393 → **T3 · Grade de gráficos 2×2**.
Galeria das 20 opções em [`issues-392-393-visual/`](issues-392-393-visual/) (`node docs/planos/issues-392-393-visual/build-gallery.cjs`).

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-06 |
| Branch | `feat/issues-392-393` (a criar a partir de `main` em `066d2ecd`) |
| Autor dos commits | skill `usage-monitor-commit-push`, trailer `Co-Authored-By: Claude Opus 5.5` |

### ▶ Atividade corrente
A06 — analytics por turno do Codex.

### ⏭ Próxima atividade
A07 — grade 2×2 nas duas fontes.

- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Diagnóstico (evidência)

### #392 — Histórico

| # | Problema relatado | Evidência no código | Estado |
| --- | --- | --- | --- |
| H1 | "7 dias" (período) e "7d" (janela) lado a lado | `HistoryControls.kt`: `HistoryRangeSegments` e `quota()` na mesma `AppToolbar`, sem rótulo | confirmado |
| H2 | tabela de janelas e barras horárias confusas | `HistoryWindowAnalysisPanel`: tabela + barras do **intervalo inteiro** sob o título "Janelas 5h"; as barras não dizem de que janela são | confirmado |
| H3 | barras horárias sem dica | `HistoryHourlyDistribution` (`HistoryWindowAnalysis.kt`) não tem tratamento de ponteiro | confirmado |
| H4 | legenda da janela ativa pouco clara | `currentActiveSpanCaption` é frase fixa sobre a janela **aberta**; com zoom em outro trecho (print 4: "Ver tudo" visível, eixo 02/10→05/10) ela descreve uma faixa fora da tela | confirmado |
| H5 | (achado) rótulo do meio do eixo errado | `buildTimeReferenceLabels` (`UsageHistoryChartModel.kt:278`) pega o ponto do meio **por índice**; o eixo X é por tempo (`UsageHistoryChartModel.kt:55-64`). Print 1 (24h, 20:57→20:54): mostra 13:39, o meio é 08:55 | confirmado |
| H6 | (achado) linha "21:02 → 21:02 · 0min" | janela já usada na primeira leitura começa nela (regra da #382) — dado correto, texto enganoso | confirmado |

### #393 — Sessões CLI

| # | Problema | Evidência | Estado |
| --- | --- | --- | --- |
| C1 | Codex sem gráficos | `CodexCliSessionDetailContent` (`CodexCliSessionsScreen.kt:402`) só tem metadados, 5 blocos e tabela | confirmado |
| C2 | tabela do Codex pouco útil | coluna "Resposta" = id truncado; tokens por `totalTokens.toString()` ("35983") contra `formatQuantity` no resto da tela | confirmado |
| C3 | Claude: rótulo cortado "Taxa de acerto do…" | print 1 da #393 | confirmado no print; causa (largura do `MetricCard` em `FlowRow`) a medir em A07 |
| C4 | Claude: gráficos escondidos | só "Contexto por turno" fora do `AdvancedDisclosure` | confirmado |

**Limites do dado do Codex** (valem para T3): por resposta há `inputTokens`, `cachedInputTokens`,
`outputTokens`, `reasoningOutputTokens` e `throughput` (`CodexCliSessionModels.kt`). **Não há** tarifa
de modelo GPT na `ModelPricingTable` (custo e economia ficam fora, nunca como zero) nem tamanho da
janela de contexto do modelo (percentual de janela fica fora).

**Fora do escopo, por decisão:** o custo do Claude com 4 casas (`formatMicrosUsd`) é formato
existente e deliberado; não muda nesta entrega.

## Decisões

1. **S9 substitui a seção "Janelas e distribuição horária"** de `HistorySeriesCard`: lista de janelas
   à esquerda (mais nova primeiro, até `HISTORY_WINDOW_ROW_LIMIT`), detalhe da janela escolhida à
   direita — métricas (pico, esgotou em, ritmo, ativa), curva **só daquela janela** com a faixa ativa,
   e barras horárias **só daquela janela**. Seleção padrão: a janela aberta; sem ela, a mais nova.
   O gráfico principal do intervalo e os KPIs ficam como estão. A seção passa a nascer **aberta**
   (hoje nasce fechada): é ela que responde a issue.
2. **Distribuição horária por janela sai da camada data**, dos pontos crus: no intervalo Total
   `series.points` é reamostrado (`downsamplePoints`, `MAX_TOTAL_POINTS_PER_SERIES`) e a soma por hora
   ficaria errada. Campo novo com default em `QuotaWindowSummary` (`hourlyDistribution:
   QuotaHourlyDistribution? = null`) — retrocompatível, nenhum valor novo de enum.
3. **A curva da janela no detalhe** usa os pontos de `series.points` recortados por
   `firstObservedAt..lastObservedAt` — é só desenho; no Total ela herda a reamostragem que o gráfico
   principal já tem.
4. **Abaixo de 600dp** a lista vira `AppMenu` ("Janela: 06/10 16:06 · atual ▾") acima do detalhe.
5. **H3 e H4 entram junto** (a issue os pede e S9 sozinha não os resolve): dica por barra horária
   (`AppTooltip`, hora + % do consumo) e a frase da faixa ativa vira legenda com amostra de cor das
   marcas do gráfico; com zoom fora da janela aberta, a frase diz que ela está fora do trecho.
6. **H6:** janela sem subida observada depois da primeira leitura mostra "antes da 1ª leitura" no
   lugar de "21:02 → 21:02 · 0min". Nenhum formato novo de percentual ou reset.
7. **T3 vale para as duas fontes**, mesma primitiva `CliTurnChartGrid` (2 colunas ≥ 600dp, 1 abaixo,
   sem `FlowRow + weight`):
   - Codex: Contexto (entrada da resposta, ▼ compactação) · Cache (% da entrada servida do cache) ·
     Saída (com raciocínio) · Vazão.
   - Claude: Contexto · Cache · Saída · Custo acumulado (com economia, já existente).
   - "Avançado" do Claude fica com blocos, distribuição de custo, economia e escrita de cache 5m/1h.
8. **Turno sem vazão medida não é zero** (`null` = não medido): o gráfico de vazão pula o turno, não
   o desenha no chão. Se `TurnSeriesChart` não suportar lacuna, o suporte entra na mesma atividade.
9. **Tabela do Codex:** sai "Resposta"; entram Contexto, Cache, Saída e Vazão com `formatQuantity` /
   `formatThroughput`.

## Execução — uma atividade, um commit

| # | Atividade | Arquivos principais | Teste |
| --- | --- | --- | --- |
| A01 | Plano + galeria S/T | `docs/planos/issues-392-393-execucao.md`, `docs/planos/issues-392-393-visual/` | build da galeria sem erro |
| A02 | H5: rótulo do meio do eixo pelo **tempo** do meio | `UsageHistoryChartModel.kt` | `commonTest` com amostragem desigual (60 s e 5 min) |
| A03 | Distribuição horária por janela na camada data | `QuotaWindowAnalysis.kt`, `UsageHistoryRepositoryImpl.kt` | `QuotaWindowAnalysisTest`: soma por janela = distribuição do intervalo; janela parcial no início do intervalo |
| A04 | S9: lista de janelas + detalhe (curva, faixa ativa, métricas, barras da janela), versão estreita, H6 | `HistoryWindowAnalysis.kt` dividido (`HistoryWindowList.kt`, `HistoryWindowDetail.kt`), `HistorySeriesCard.kt` | `desktopTest` da tela: seleção padrão, troca de janela, < 600dp, "antes da 1ª leitura" |
| A05 | H3 + H4: dica nas barras horárias; legenda com amostras e frase que acompanha o zoom | `HistoryWindowDetail.kt`, `HistorySeriesCard.kt`, `UsageHistoryLineChart.kt` | `desktopTest`: hover mostra hora e %; zoom fora da janela muda a frase |
| A06 | Analytics por turno do Codex no domain (contexto, cache %, saída, raciocínio, vazão anulável) | `domain/entity/CodexCliSessionAnalytics.kt` | `commonTest`: compactação, turno sem `requestTs`, entrada zero |
| A07 | T3: `CliTurnChartGrid`; detalhe do Codex extraído para arquivo próprio com grade e tabela nova; Claude com a grade (séries de saída e cache por turno em `CliSessionAnalytics`), C3 | `CliTurnChartGrid.kt`, `CodexCliSessionDetail.kt`, `CliSessionDetail.kt`, analytics do Claude | `desktopTest` das duas telas; `ArchitectureRulesTest` (≤ 800 linhas) |
| A08 | Docs e registro: `presentation.md`, protótipo (§ Histórico e § Sessões CLI), kit `.jsx`, tabela de rodadas da skill (S e T) | docs | — |
| A09 | `gradlew.bat allTests`, capturas (`generateScreenshots`) conferidas, fechamento do plano | — | suíte inteira |

Commit e push só com autorização.

## Pontos de situação

| Atividade | Commit (assunto) | Evidência |
| --- | --- | --- |
| A01 | `docs: plan issues #392 and #393 with visual gallery` | `node docs/planos/issues-392-393-visual/build-gallery.cjs` → 65,0 KB; aberta no browser pane: console sem erro, 22 cards, nenhum palco com transbordo horizontal |
| A02 | `fix(history): label the axis middle by time, not by point index` | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.components.UsageHistoryLineChartTest"` → 32 testes, 0 falhas (1 novo: polling 5 min + 60 s, centro 08:55 BRT); `--tests "com.usagemonitor.ui.History*"` → 20 testes, 0 falhas |
| A03 | `feat(history): compute the hourly distribution per quota window` | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.QuotaWindowAnalysisTest" --tests "com.usagemonitor.data.*History*" --tests "com.usagemonitor.architecture.*"` → 17 + 39 + 8 testes, 0 falhas (2 novos: soma das janelas = distribuição do intervalo, hora a hora; janela sem subida → `null`) |
| A04 | `feat(history): list quota windows with a per-window detail` | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.HistoryWindowAnalysisTest" --tests "com.usagemonitor.ui.History*" --tests "com.usagemonitor.presentation.History*" --tests "com.usagemonitor.architecture.*"` → 62 testes, 0 falhas (novos: seleção padrão, recorte de pontos, segunda linha da lista, "usada antes da 1ª leitura", itens sem sobreposição, troca do detalhe no clique, menu abaixo de 600dp); `gradlew.bat generateScreenshots -PscreenshotScenario=history-regression -PscreenshotOutputDir=build/issue392-screenshots` → lista e detalhe conferidos em `history-windows-dark-100.png` |
| A05 | `feat(history): hint hourly bars and key the active band to the view` | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.HistoryWindowAnalysisTest" --tests "com.usagemonitor.presentation.ui.components.UsageHistoryLineChartTest" --tests "com.usagemonitor.ui.History*" --tests "com.usagemonitor.presentation.History*" --tests "com.usagemonitor.architecture.*"` → 98 testes, 0 falhas (novos: texto da bolha, hora sob o ponteiro, chave dentro/fora do trecho, hover real na barra 17h); captura `history-regression-anthropic-dark-1030-640-125.png`: chave com amostra e eixo 12:00 · 23:30 · 11:00 |

## Problemas em aberto e riscos

| Risco | Estado | Mitigação |
| --- | --- | --- |
| Curva da janela no Total herda a reamostragem do gráfico principal | aceito | só desenho; números do detalhe vêm de `QuotaWindowSummary`, calculados dos pontos crus |
| Janela que começou antes do início do intervalo aparece parcial (curva e barras) | aberto | rotular "parcial" no item da lista; medir em A04 |
| `TurnSeriesChart` aceita só `List<Long>` (sem lacuna) | aberto | decisão 8; medir em A07 |
| Entrada por resposta do Codex = tamanho do contexto | aberto | coerente com o print (35.983 → 91.754 crescendo); confirmar contra um rollout real em A06 antes de rotular "Contexto" |
| `CodexCliSessionsScreen.kt` (588) e `CliSessionDetail.kt` (556) perto do teto de 800 | aberto | A07 começa extraindo |
| Suíte só roda no Windows; nenhum host de janela muda | aceito | PR declara a plataforma testada |

## Desvios do plano e achados da execução

- **A03:** `UsageHistoryRepositoryImpl` não mudou. Ele já chama `quotaWindowsOf(points, …)` com os pontos crus, antes de `downsamplePoints`; o campo novo nasce dentro de `quotaWindowsOf` e herda isso.
- **A04:** documentação de tela (protótipo, kit `History.jsx`, `presentation.md`, tabela de rodadas da skill) sai da A08 e entra no commit de cada atividade de tela, como manda o `CLAUDE.md` ("no mesmo commit da mudança"). A08 fica só com o que sobrar.
- **A04:** sem divisória vertical entre lista e detalhe — `IntrinsicSize.Min` falha com o `BoxWithConstraints` de `HistoryMetricTable`.
- **A04:** "usada antes da 1ª leitura" vale também no PDF (`activeSpanLabel` ganhou o idioma).
- **A05:** a primeira versão da bolha abria por cima das barras e travou o teste de hover por mais de 10 min (laço `Enter`/`Exit` com o `Surface`; pilha do worker parada em `HistoryLayoutRegressionTest.kt:68`). Corrigido com a bolha acima das barras.
