# Análise e Histórico mais claros, no app e no terminal (#463; rodadas AH e AI)

## Ponto de situação

**Estado atual:** `Aguardando a escolha do usuário — A00 concluída (plano e galeria); A01 em diante dependem da direção.`

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (CLI, Linux) |
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
| U1 | Direção do app (AH1–AH5, pode combinar) | _pendente_ |
| U2 | Direção do terminal (AI1–AI5, pode combinar) | _pendente_ |

## Execução — uma atividade, um commit

A00 não depende da escolha. As demais são o rascunho para a direção que vier e serão reescritas com ela.

| # | Atividade |
| --- | --- |
| A00 | `docs`: plano e galeria AH/AI |
| A01 | `fix(history)`: correções que valem para qualquer direção — eixo Y escrito na curva do Histórico (D1), `paceLabel` com uma casa abaixo de 10 (D3), rótulos sem corte (D6); teste de tela no arquivo da tela; protótipo e design system no mesmo commit |
| A02 | `feat(analysis)`: janela Analysis na direção escolhida — uma escala por gráfico (D2), ritmo seguro só quando decide algo (D4), o gráfico da pergunta principal (D7); modelo puro testado em `commonTest`; Ajuda e GIF, protótipo, `presentation.md` e histórico da skill no mesmo commit |
| A03 | `feat(history)`: janela History na direção escolhida — 5h e 7d sem rolagem (D5); mesmos registros de A02 |
| A04 | `feat(cli)`: terminal na direção escolhida (D8) — função pura de tela (`TuiScreen`), glifos só da tabela, quatro paletas; `docs/design-system/cli/readme.md` e `prototipo-modo-cli.html` no mesmo commit |

## Pontos de situação

| # | Data | Resultado | Comando e evidência |
| --- | --- | --- | --- |
| A00 | 2026-10-23 | concluída | galeria renderizada no Chrome headless (`--dump-dom`: 12 cartões, console sem erro); capturas conferidas — corrigidos a escala do eixo Y das 24 h, o detalhe esticado da AH2 e o alinhamento dos glifos do terminal (todo glifo fora do ASCII em célula de 1ch) |

## Problemas em aberto e riscos

- **AH4** junta duas janelas: muda a navegação e os dois view models (`AnalysisViewModel`, `HistoryViewModel`); é a de
  maior risco e esforço. `TeamUsageViewModel` e `DashboardViewModel` estão perto do teto de 800 linhas — a mudança não
  deve passar por eles.
- **AH3** depende de histórico suficiente (mínimo de 5 janelas anteriores); com menos, a faixa some e a tela diz isso.
- **Linux (X11)**: mudança em host de janela é aberta no Linux antes do merge, ou o PR diz o risco; a suíte só roda no
  Windows.
