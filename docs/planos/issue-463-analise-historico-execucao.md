# Análise e Histórico mais claros, no app e no terminal (#463; rodadas AH e AI)

## Ponto de situação

**Estado atual:** `Em execução (2026-10-27, Windows 11) — A00 a A03 concluídas e verificadas; A04, A06 e A05 por fazer.`

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (CLI; A00–A03 no Linux, da verificação da A03 em diante no Windows 11; no Linux, a suíte roda aqui com `-Dorg.gradle.java.home` no JDK 17, porque o Gradle global aponta para um JDK 8) |
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
| U3 | A05: aba nova exige valor novo no enum `TuiTab` (exceção à regra "nenhum valor novo em enum") | **`ANALYSIS(6)` no fim** (2026-10-27): os números das abas 1–5 não mudam |

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
| A02 | 2026-10-23 | concluída | `./gradlew -Dorg.gradle.java.home=<jdk17> desktopTest` (Linux): 3 273 testes, 0 falhas (a primeira passada falhou em 2: o teto de `== AppLanguage.PT` caiu para 706 e foi baixado; o teste de tela achava duas diagonais, uma por janela). `generateHelpMedia -PhelpMediaTopic=session-analysis` (26 quadros); quadros do GIF e captura da tela inteira conferidos — o ponto ficava na leitura antiga quando o percentual andava (o caminho agora termina na leitura de agora) e "1 pontos" virou singular; a frase e a projeção seguem o veredito do card (`projectedAtReset`) |
| A03 | 2026-10-27 | concluída, **verificação pendente** | `./gradlew -Dorg.gradle.java.home=<jdk17> desktopTest` (Linux): 3 275 testes, 1 falha (`HistoryAdequacyTest` › `expanded window readings remain complete in narrow modal`: a 320dp o botão do menu "Janela: … ▾" corta o rótulo). O teste foi ajustado para conferir a descrição do menu e a linha de fatos da janela (que quebra), **sem nova passada** — a sessão parou aqui. Antes: 2 testes de tela que liam o detalhe antigo (S9) foram atualizados. Captura de 5h e 7d lado a lado conferida. Desvio da galeria: chips viraram o menu de janelas (design system: chip liga/desliga restrição) |
| A03 | 2026-10-27 | verificação concluída | `gradlew.bat allTests` (Windows 11): 3 275 testes, 1 falha — o mesmo `HistoryAdequacyTest` › `expanded window readings remain complete in narrow modal`, agora com `Expected exactly '1' node but found '2'` na tag `historyWindowMenu`: com 5h e 7d lado a lado há um menu por série. O teste passa a conferir os dois menus (`assertCountEquals(2)`) e ler o primeiro, o da série `first()`; `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HistoryAdequacyTest" --tests "com.usagemonitor.ui.HistoryLayoutRegressionTest"`: 12 testes, 0 falhas |

## Problemas em aberto e riscos

- **Seletor de conta do Histórico** (parte do D6): continua cortando o rótulo longo no botão; o menu mostra o nome completo.
  Fica fora desta entrega.
- **Início nominal da janela**: a diagonal usa reinício − duração (5 h ou 7 d), como a Análise já fazia. O Histórico
  evitava o início nominal ("a API não informa"); a lista continua dizendo o início **observado**, e o eixo do gráfico
  diz "início" e "reinício".
- `TeamUsageViewModel` e `DashboardViewModel` estão perto do teto de 800 linhas — a mudança não passa por eles.
- **Linux (X11)**: mudança em host de janela é aberta no Linux antes do merge, ou o PR diz o risco; a suíte só roda no
  Windows.

## Como continuar (handoff de 2026-10-27)

1. `git fetch && git switch feat/analysis-history-clarity-463` (ou `git worktree add ../usage-monitor-463 feat/analysis-history-clarity-463`).
2. **Primeiro**, rodar `gradlew.bat allTests` (Windows) e confirmar o ajuste do `HistoryAdequacyTest` da A03. No Linux desta
   sessão: `./gradlew -Dorg.gradle.java.home=<jdk17> desktopTest` (o Gradle global apontava para um JDK 8).
3. Seguir A04 → A06 → A05, uma atividade por commit, com a linha na tabela "Pontos de situação" no mesmo commit.

### A04 — aba 3 do terminal: tabela de janelas (AI4) + gráfico alto no `enter` (AI1)

- `TuiHistoryRow(account, quota, windows: List<QuotaWindowSummary>, points: List<UsageHistoryPoint>)` no lugar de `spark`;
  `listedWindows` = mais nova primeiro, até 8. O `TuiReader.history` já tem a série: passar `series.windows` e `series.points`.
- `TuiHistoryState(range, row, window, chartOpen)`: `↑↓` andam pelas janelas de todas as cotas (pulando cota sem janela),
  `←→` trocam o período (só com a tabela), `enter` abre o gráfico, `esc` fecha. `TuiContext.historyRowCount` vira
  `historyWindowCounts: List<Int>`; o `enter` da aba 1 (`openHistory`) passa a `copy(row = linha, window = 0, chartOpen = false)`.
- Tela (`TuiHistoryView`): linha "período  24 h  7 dias  30 dias … uma linha = uma janela"; cabeçalho fixo
  `INÍCIO · ATIVA · PICO · COMO TERMINOU`; um bloco por cota (`● trabalho Claude 5h  91% crítico · 7 janelas no período ·
  1 esgotou`) e uma linha por janela: `23/10 15:09`, ativa `15:09–19:07` (ou `20/10–23/10` se atravessa dias), pico em
  `TerminalPrimitives.quotaBar` de 10 células + `%`, e o fim: `aberta · reinicia 19:30`, `sobrou 4%` ou `▲ esgotou após 3 h 6 min ·
  parado 1 h 54 min` (vermelho). Rolagem por `TuiFrame.scrollStart` na linha escolhida.
- Gráfico (`enter`): título `rótulo · janela de 23/10 15:09 a 20:09 · uma coluna = 10 min`; 8 linhas, eixo `100% ┤ / 50% ┤ / 0% ┤`
  (glifos da moldura), colunas = largura disponível até 60, valor = última leitura até o fim da coluna (`null` antes da 1ª
  leitura e depois de agora), cheio `█`, parcial pelos `sparkLevels`; base `╰───┆`; rótulos início/meio/reinício de
  `PaceDiagonalModels.axisLabels`; frase: aberta → "64% usada com 97% da janela já passada: 33 pontos abaixo da linha";
  fechada → "pico 96% · sobrou 4%" ou "pico 100% · esgotou após …". Duração por `PaceDiagonalModels.durationLabel`.
- Rótulos novos em `TuiHistoryLabels` via `localized` (nunca `== AppLanguage.PT`; o teto do `ArchitectureRulesTest` é catraca).
- Atualizar `TuiHistoryViewTest`, `TerminalMediaFixtures` (desktopTest), `docs/design-system/cli/readme.md` e a tela 3.3 de
  `docs/planos/prototipo-modo-cli.html` (+ tela nova do gráfico) no mesmo commit.

### A06 — `status` e linha de status com a linha "tempo" (AI5)

- Em `TerminalStatusView`, abaixo de cada cota com duração conhecida (`quotaWindowLengthMillis(label, periodType)`) e
  `resetsAt`: sublinha `tempo  ████░ 97%  4h51 de 5h` e, na coluna de estado, `✓ 33 pp atrás do tempo` / `▲ 40 pp à frente
  do tempo`. `--json` ganha campo novo (sem mudar os existentes). `TerminalStatusLineView`: `5h 64%/97% ✓`.

### A05 — aba Análise (AI2) + corrida (AI3) + comando `usage-monitor analysis`

- **Decidido (U3, 2026-10-27):** `ANALYSIS(6)` no fim do enum `TuiTab` — exceção à regra "nenhum valor novo em enum",
  aprovada pelo usuário; os números das outras abas não mudam. Todo `when` sobre `TuiTab` é auditado no mesmo commit.
- Conteúdo: as duas janelas lado a lado (barra, veredito em palavra, ritmo última hora/médio, "para caber"), 24 h numa
  escala só com tok/s em linha própria; corrida: barras "reinicia" e "esgota" (ritmo da última hora e médio) na mesma
  régua (`exhaustionTimeOf` do domínio). `usage-monitor analysis` imprime a corrida, com `--json` como contrato.
  Dados: `buildSessionAnalysis` com o histórico (vazão pode ficar nula sem o índice CLI).

### Depois de A06/A05

`allTests` + relatório da skill `usage-monitor-test-report`, PR (dizer: testado no Linux; host de janela não mudou), CI até
`ci-ok`, merge, apagar branch e worktree.
