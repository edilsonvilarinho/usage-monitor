# Análise e Histórico mais claros, no app e no terminal (#463; rodadas AH e AI)

## Ponto de situação

**Estado atual:** `Em andamento — A00 e A01 concluídas; A02 (Análise AH1) a seguir.`

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (CLI, Linux; a suíte roda aqui com `-Dorg.gradle.java.home` no JDK 17, porque o Gradle global aponta para um JDK 8) |
| Data | 2026-10-23 |
| Branch | `feat/analysis-history-clarity-463` (worktree `../usage-monitor-463`) |
| Autor dos commits | identidade temporária `claude`, trailer `Co-Authored-By: Claude Opus 5.5` |

- Galeria em [`issue-463-visual/options.html`](issue-463-visual/options.html): rodada **AH** (app: janelas Analysis e
  History, 5 opções + hoje) e rodada **AI** (modo terminal, 5 opções + hoje), com os dois prints da issue como referência.
- Pedido do usuário: "essa tela ainda tá muito confusa, não tá clara o suficiente para mostrar os dados de forma clara e
  objetiva usando gráfico", 5 sugestões pela skill `usage-monitor-visual-options`, e o mesmo para o modo CLI; e "preciso
  tirar 2 prints" para ver o Histórico.

## Diagnóstico (evidência)

| # | Ponto | Evidência | Estado |
| --- | --- | --- | --- |
| D1 | Curva do Histórico sem escala | `UsageHistoryPlotDrawing.kt` desenha 3 linhas de grade e nenhum texto no eixo Y; a 7d em 94% parece cheia (print 2) | confirmado |
| D2 | 24 h da Análise com duas escalas no mesmo espaço | `AnalysisHoursChart.kt`: barras de pp (máx. 32) e linha de tok/s (máx. 196) no mesmo Canvas, escala só na legenda (print 1) | confirmado |
| D3 | "Pace 0 %/h" na 7d com 94% de pico | `paceLabel` (`HistoryWindowAnalysis.kt:100`) arredonda com `roundToLong`; o valor real é ~0,3 | confirmado |
| D4 | Ritmo seguro que não decide nada | `AnalysisPanelModel.kt:56–60`: "safe 249.9 room left" com 9 min para o reinício, "safe 0.1 above the safe pace" na semanal (print 1) | confirmado |
| D5 | Histórico precisa de duas rolagens | 5h e 7d empilhados em `HistoryWindowAnalysisPanel` (print 2: dois prints para ver tudo) | confirmado |
| D6 | Rótulos cortados | "23/10 15:09 BRT · curre…" na lista e o e-mail da conta no seletor (print 2) | confirmado |
| D7 | A pergunta "vou estourar, quando?" não tem gráfico | só o tique na `AppProgressTrack` e o texto "≈66% at the reset"; o resto são 30 números em duas tabelas (print 1) | confirmado |
| D8 | O terminal não tem análise | `TuiHistoryView.kt` desenha uma sparkline de uma linha por cota; ritmo e projeção não existem na TUI nem no `status` | confirmado |

## Decisões do usuário

| # | Pergunta | Resposta |
| --- | --- | --- |
| U1 | Direção do app (AH1–AH5, pode combinar) | **AH1 · Diagonal do ritmo** (2026-10-23) |
| U2 | Direção do terminal (AI1–AI5, pode combinar) | **AI1 a AI5, todas** (2026-10-23): aba Histórico vira a tabela de janelas (AI4) e `enter` abre o gráfico alto (AI1); aba nova Análise (AI2) com a corrida (AI3); comando `analysis` com a corrida (AI3); `status` e linha de status com a linha "tempo" (AI5) |

## Execução — uma atividade, um commit

| # | Atividade |
| --- | --- |
| A00 | `docs`: plano e galeria AH/AI |
| A01 | `fix(history)`: correções que valem para qualquer direção — eixo Y escrito na curva do Histórico (D1), `paceLabel` com uma casa abaixo de 10 (D3), início da janela na lista sem corte (D6); protótipo e `presentation.md` no mesmo commit |
| A02 | `feat(analysis)`: janela Analysis na direção AH1 — domínio `QuotaWindowPace.kt` (caminho da janela, fração do tempo, instante em que esgota), `AnalysisWindowView` com o caminho da janela aberta e das anteriores, gráfico da diagonal com frase (D7), ritmo seguro só na frase (D4), 24 h com uma escala e tok/s em faixa própria (D2), intervalos em %/h; a tabela "esta janela × anterior" sai (as anteriores ficam desenhadas atrás); Ajuda e GIF, protótipo, `presentation.md` e histórico da skill no mesmo commit |
| A03 | `feat(history)`: janelas do Histórico na diagonal — 5h e 7d lado a lado (D5), uma janela escolhida por chip; série sem duração nominal conhecida (MiniMax intervalar, mensal) fica com a lista e o detalhe de antes |
| A04 | `feat(cli)`: aba Histórico vira a tabela de janelas (AI4) e `enter` abre o gráfico alto da janela (AI1); design system do terminal e protótipo do CLI no mesmo commit |
| A05 | `feat(cli)`: aba Análise (AI2) com a corrida esgotar × reiniciar (AI3), e o comando `usage-monitor analysis` com a mesma corrida (`--json` como contrato) |
| A06 | `feat(cli)`: `status` e linha de status com a linha "tempo" da janela (AI5) |

## Pontos de situação

| # | Data | Resultado | Comando e evidência |
| --- | --- | --- | --- |
| A00 | 2026-10-23 | concluída | galeria renderizada no Chrome headless (`--dump-dom`: 12 cartões, console sem erro); capturas conferidas — corrigidos a escala do eixo Y das 24 h, o detalhe esticado da AH2 e o alinhamento dos glifos do terminal (todo glifo fora do ASCII em célula de 1ch) |
| A01 | 2026-10-23 | concluída | `./gradlew -Dorg.gradle.java.home=<jdk17> desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*"` (Linux): 1 470 testes, 0 falhas; testes novos de `buildValueLabels` (100/50/0) e `paceLabel` (0,3 %/h) |

## Problemas em aberto e riscos

- **Seletor de conta do Histórico** (parte do D6): continua cortando o rótulo longo no botão; o menu mostra o nome completo.
  Fica fora desta entrega.
- **Início nominal da janela**: a diagonal usa reinício − duração (5 h ou 7 d), como a Análise já fazia. O Histórico
  evitava o início nominal ("a API não informa"); a lista continua dizendo o início **observado**, e o eixo do gráfico
  diz "início" e "reinício".
- `TeamUsageViewModel` e `DashboardViewModel` estão perto do teto de 800 linhas — a mudança não passa por eles.
- **Linux (X11)**: mudança em host de janela é aberta no Linux antes do merge, ou o PR diz o risco; a suíte só roda no
  Windows.
