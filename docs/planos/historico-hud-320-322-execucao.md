# Histórico por janela 5h/7d (#320) e HUD menos flat (#322) — execução

- **Modelo:** Claude Opus 5.5 (`claude-opus-5-5`), Claude Code CLI
- **Data de início:** 2026-09-27
- **Branch:** `feat/320-322-history-hud`
- **Autor dos commits:** identidade temporária `claude <claude@anthropic.com>`
  (`.claude/skills/usage-monitor-commit-push`)

## Contexto

**[#320](https://github.com/edilsonvilarinho/usage-monitor/issues/320).** A janela Histórico da
Anthropic plota só a série 5h: `buildGenericHistoryGroups` (`HistoryScreenFormatting.kt`) manda o
`INTERVAL` para o gráfico e o `WEEKLY` vira só painel de métricas. Não há como ver a progressão da
7d nem escolher a janela. "Consumido no período 173%" é `positiveDeltaOf` somado através de
reinícios e dividido pelo total de **uma** janela. A "Previsão" aparece cortada ("A janela deve
reiniciar antes do") porque o valor do `MetricItem` tem `maxLines = 1` sem `overflow` (clip). As
animações da janela usam `tween` cru e ignoram `AppMotionPolicy`, e toda troca de intervalo pisca
`Success → Loading → Success`.

**[#322](https://github.com/edilsonvilarinho/usage-monitor/issues/322).** A marca do provedor no
anel da HUD fica pequena (`hudRingMarkSize`: 19,6 / 14 / 8,4dp com 1/2/3 arcos num anel de 36dp). A
palavra de risco é `Text` puro, sem fundo nem ponto. A única animação da marca é o giro durante a
recoleta.

## Decisões do usuário

1. Gráfico: controle segmentado `5h | 7d | Ambas`; "Ambas" sobrepõe as duas linhas com legenda
   (o protótipo §5 já desenha assim).
2. Análise que substitui "Consumido no período": tabela de janelas, resumo agregado e distribuição
   do consumo por hora BRT.
3. HUD: palavra de risco numa pílula tonal com ponto; percentual da pior janela no tom.
4. Marca do provedor: pulso único a cada coleta nova (não contínuo).

## Execução — uma atividade, um commit

| # | Atividade |
|---|---|
| A0 | Este documento |
| A1 | "Previsão" cortada: valor do `MetricItem` quebra em até duas linhas com reticências |
| A2 | Extração do desenho do gráfico para `UsageHistoryPlotDrawing.kt`, sem mudança de comportamento |
| A3 | Gráfico multi-série com legenda e tooltip de todas as séries; revelação por `appTween` |
| A4 | Domínio: resumo por janela, resumo agregado e distribuição por hora BRT, calculados antes da amostragem do TOTAL |
| A5 | Seletor `5h \| 7d \| Ambas` (`HistoryQuotaView`, enum novo); Codex agrupado; "Consumido no período" sai |
| A6 | Tabela de janelas e barras por hora (`HistoryWindowAnalysis.kt`) |
| A7 | Movimento: troca de intervalo sem piscar `Loading`; entradas pela política |
| B1 | HUD: anel 36 → 44dp, marca maior pela fórmula existente |
| B2 | `AppStatusPill` (primitiva nova) adotada na HUD, com geometria e medição de texto |
| B3 | HUD: percentual da pior janela no tom de risco |
| B4 | HUD: pulso da marca a cada coleta nova, atrás da política de movimento |

## Pontos de situação

| # | Commit | Comando | Resultado |
|---|---|---|---|
| A0 | docs: plan for history 5h/7d view and HUD status pill (#320, #322) | revisão do diff | documento criado |
| A1 | fix: wrap history forecast metric instead of clipping it (#320) | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HistoryScreenTest"` | 9 testes, 0 falhas. Com `maxLines = 1` restaurado o teste novo falha: "previsão com 16 px contra 16 px de uma linha" |
| A2 | refactor: move history chart drawing out of the chart composable (#320) | `gradlew.bat allTests` | 2254 testes, 0 falhas, 0 ignorados; `UsageHistoryLineChart.kt` 756 → 375 linhas, `UsageHistoryPlotDrawing.kt` 412; nenhum teste editado |
| A3 | feat: overlay weekly quota on the history chart (#320) | `gradlew.bat allTests` | primeira passada: 2261 testes, 1 falha — `ArchitectureRulesTest`: `UsageHistoryLineChart` com 312 linhas (limite 300). Bolha do tooltip extraída para `HistoryTooltipLayer`; segunda passada 2261 testes, 0 falhas, 0 ignorados |
| A4 | feat: per-window quota analysis for history series (#320) | `gradlew.bat allTests` | 2270 testes, 0 falhas, 0 ignorados; `QuotaWindowAnalysisTest` 8, `UsageHistoryRepositoryImplTest` 32 (inclui pico de 100% num índice que a amostragem do Total descarta) |
| A5 | feat: 5h/7d/both quota selector in history (#320) | `gradlew.bat allTests` | 2277 testes, 0 falhas, 0 ignorados. No caminho, o teste antigo do card do Claude passou a achar dois nós "5h" (seletor e legenda); a asserção virou contagem de 2 |
| A6 | feat: quota window table and hourly usage bars in history (#320) | `gradlew.bat allTests` | 2283 testes, 0 falhas, 0 ignorados; `HistoryWindowAnalysisTest` 5, `HistoryScreenTest` 13 |
| A7 | feat: keep history content on screen while reloading (#320) | `gradlew.bat allTests --rerun` | 2284 testes, 0 falhas, 0 ignorados. Antes: três passadas com `DashboardViewModelRefreshPersistenceTest` vermelho (timeout de tempo real) enquanto existia o teste novo `selectSource still goes through Loading`; `main` num worktree deu 2253/0; sem os dois testes novos 2283/0; só com `selectRange keeps the previous content` 2284/0. O teste de troca de fonte saiu — ver achados |
| B1 | feat: enlarge HUD ring to 44dp so provider marks stay legible (#322) | `gradlew.bat allTests` | 2285 testes, 0 falhas, 0 ignorados; marca 25,2 / 19,6 / 14dp (1/2/3 arcos); `hudRest()` do gerador de capturas (64dp) comporta 44 + 2×8 |
| B2 | feat: tonal status pill for HUD accounts (#322) | `gradlew.bat allTests` e `gradlew.bat allTests --rerun` | Primeiro os testes novos pegaram dois defeitos: pílula até 0,6dp mais larga que a estimativa entre 110% e 144% (folga de 1dp adicionada) e verde do tema claro a 4,16:1 com fundo a 14% (fundo baixado para 8%, pior caso 4,53:1). Suíte: 1ª passada 2288 testes com 1 falha em `DashboardViewModelRefreshPersistenceTest` (a mesma do A7); 2ª passada 2288, 0 falhas |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| `UsageHistoryLineChart.kt` tem 756 linhas; sem A2 antes, A3 estoura o teto de 800 do `ArchitectureRulesTest` | fechado (A2) |
| Codex passa de dois cards (5h, 7d) para um card com seletor — muda o que o usuário já via | aceito |
| Pílula e anel maior engrossam o notch; `HUD_MAX_ALONG_FRACTION` (0,45) pode levar ao modo compacto mais cedo em tela pequena | aberto — só verificável no app em 1366px |
| `DashboardViewModelRefreshPersistenceTest` falha intermitente na suíte completa (espera de 5 s em tempo real) | aberto — pré-existente, fora do escopo |
| Contraste do texto da pílula sobre o fundo tingido nos dois temas | fechado (B2: 8%, `AppStatusPillContrastTest`) |
| Caminho de atividade observada (OpenCode/Kilo/Gemini) continua escolhendo a série pelo intervalo, sem o seletor | aceito (fora do escopo) |

## Desvios do plano e achados da execução

- **`DashboardViewModelRefreshPersistenceTest` é instável na suíte completa — conclusão do A7
  corrigida no B2.** No A7, `allTests` falhou três vezes seguidas em `refresh persists the new scheduled
  time via callback` (`Condition not met within real-time timeout`) e passou depois que o teste
  `selectSource still goes through Loading` saiu de `HistoryViewModelTest`; isso foi lido como causa.
  **Não era**: no B2, sem aquele teste, a mesma falha voltou numa passada e sumiu na seguinte. O teste
  do dashboard espera até 5 s em tempo real e falha sob carga ou ordem desfavorável da suíte; isolado
  passou 3/3 e na `main` (worktree, 2253 testes) passou. Não mexe em código desta branch; fica
  registrado como instabilidade pré-existente, sem correção aqui. O teste removido do A7 não volta —
  afirmava comportamento que já existia.
- **A7 — falha de leitura continua publicando `Error`.** O plano previa manter os números na tela e
  publicar só a mensagem; ficou como era, porque exigiria um campo de erro no `Success` e mudaria o
  contrato testado do estado de erro. Pendente.
