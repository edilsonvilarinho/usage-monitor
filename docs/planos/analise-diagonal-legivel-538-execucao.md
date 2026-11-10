# Análise: diagonal legível e período maior — issue #538

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-10, branch `feat/issue-538-diagonal-legivel`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes `backend-dev`/`frontend-dev`.

## Objetivo

A janela "Análise" mostra a diagonal do ritmo (5h e semanal), as barras das últimas 24 h e a tabela de ritmo, mas o
leitor não sabe qual ritmo decidiu o veredito (semanal 7 pontos abaixo da linha e mesmo assim "esgota") e só vê a
janela atual. O dono escolheu, entre 5 protótipos, a direção **P1 "Diagonal legível"**
(`prototipos/issue-538/p1-diagonal-legivel/index.html`): mesma diagonal com legenda explícita, marcador "agora"
rotulado, valor projetado no reinício, veredito numa frase que diz o ritmo usado, e seletor de período
(Agora | 24 h | 7 d | 30 d | 90 d) que sobrepõe as janelas passadas com resumo e faz barras e tabela seguirem o período.

## Fatos confirmados (2026-11-10, `92927c0f`)

- `buildSessionAnalysis` (`domain/entity/SessionAnalysis.kt:131`) recebe a série e `now`; não tem período. Chamado
  por `SessionAnalysisReader.kt:57` (janela) e `terminal/TerminalAnalysisReader.kt:36` (CLI/TUI).
- `SessionAnalysisReader.kt:28` lê `HistoryRange.LAST_7_DAYS`. `HistoryRange` (`UsageHistoryModels.kt:8`) tem
  `LAST_24_HOURS`, `LAST_7_DAYS`, `LAST_30_DAYS`, `TOTAL` — sem 90 d; o `CLAUDE.md` proíbe valor novo em enum existente.
- Retenção do histórico local: 90 dias (`LocalUsageHistoryDataSource.kt:23`, `retentionPeriod = 90.days`).
- Janelas anteriores na diagonal: no máximo `ANALYSIS_PREVIOUS_PATHS = 3` (`SessionAnalysis.kt:73`, uso em `:211`).
- Estado da janela: `AnalysisViewModel` montado em `AppViewModels.kt:122`; tela em `AnalysisScreen.kt`, painel em
  `AnalysisPanel.kt` (123 linhas), gráfico em `PaceDiagonalChart.kt` (235) e `PaceDiagonalModel.kt` (131).

## Decisões

- Período é tipo novo do domain (`AnalysisPeriod`), não valor novo em `HistoryRange`; 90 d lê `HistoryRange.TOTAL`
  (a retenção corta em 90 d) e o domain recorta pelo início do período.
- `buildSessionAnalysis` ganha `period` com default `NOW`: CLI/TUI e Telegram ficam como estão.
- "Agora" mantém até 3 janelas anteriores (comportamento atual); nos outros períodos entram todas as janelas fechadas
  dentro do período, com resumo: quantidade, quantas esgotaram, pico médio. Número que o histórico não tem não é derivado.
- Barras seguem o período: 24 h por hora (atual), 7 d pico por dia, 30/90 d pico por semana.
- Texto novo por `localized`/objeto de rótulos; período escolhido vive no `AnalysisViewModel`, não na composable.

## Atividades

| ID | O quê | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Domain: `AnalysisPeriod`, janelas do período, resumo e barras por período em `buildSessionAnalysis` (TDD em commonTest) | backend-dev |
| A03 | Leitor: `sessionAnalysisReader` recebe o período e escolhe a faixa de histórico (TDD em `SessionAnalysisReaderTest`) | backend-dev |
| A04 | ViewModel + modelos: período no `AnalysisViewModel`; veredito com ritmo usado, legenda, rótulo "agora", valor no reinício em `PaceDiagonalModel`/`AnalysisPanelModel` | frontend-dev |
| A05 | Compose: seletor segmentado, legenda e rótulos no `PaceDiagonalChart`, resumo, barras por período (teste de tela) | frontend-dev |
| A06 | Registro: seção 9d do `prototipo-visual-opencode.html`, `docs/design-system/` e `docs/presentation.md` | prototype-dev |

## Ponto de situação

| ID | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano); validado por leitura dos ponteiros acima com `grep -n` | plano criado |

## Problemas em aberto e riscos

- 90 d com `TOTAL` lê até 90 d de pontos por passada ao vivo; custo de SQL não medido (hoje ~20 ms para 7 d).
- Semanal sem 4 semanas de histórico mostra resumo com menos janelas; o texto diz quantas foram lidas.
- Suíte só no Windows (#342); A05 não mexe em host de janela.
