# Análise: diagonal desenhada sem janela aberta — issue #607

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-16, branch `fix/issue-607-analysis-without-open-window`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes `frontend-dev` e `prototype-dev`.

## Objetivo

Na janela "Análise", o card 5h sem sessão aberta (captura da issue: 0 %, "sem projeção", reinício "—") não desenha
gráfico nenhum, mesmo no período "Tudo", enquanto o semanal desenha "11 janelas anteriores". O card deve desenhar a
diagonal sempre: com janela aberta como hoje; sem ela, as janelas anteriores do período atrás da linha.

## Fatos confirmados (2026-11-16, `095b8c72`)

- O domínio já entrega as anteriores sem janela aberta: `open == null` faz `previousPathsOf(paths, …)` com a lista
  inteira (`SessionAnalysis.kt:257-258`, `sed -n 237,260p`).
- Quem descarta é a apresentação: `PaceDiagonalModels.forWindow` retorna `null` sem `startsAt` ou `resetsAt`
  (`PaceDiagonalModel.kt:80-81`), e o card fica só com a frase (`AnalysisPanelModel.kt:69`).
- `windowView` só tem `resetsAt` quando a última leitura tem reinício conhecido e futuro
  (`SessionAnalysis.kt:246`): sem sessão 5h aberta, `startsAt`/`resetsAt` são nulos.
- O gráfico já aceita caminho atual vazio e `nowLabel` nulo: `drawPath` sai com lista vazia
  (`PaceDiagonalChart.kt:242`); marcador e ponto dependem de `current.lastOrNull()` (`:82`, `:140`).
- O Histórico já desenha janelas sem "agora" com eixo relativo (`forHistory`, `relativeAxisLabels`,
  `PaceDiagonalModel.kt:125-147`).
- A frase atual sem reinício diz "não há linha de ritmo" (`AnalysisPanelModel.kt:151`), testada em
  `AnalysisPanelModelTest.kt:76-83`, que afirma `diagonal == null` — o comportamento da issue.

## Decisões

- Sem janela aberta, `forWindow` devolve modelo com `current` vazio, `previous` = anteriores do período, sem
  projeção, sem `pointLabel`, sem `nowLabel`, eixo relativo (`relativeAxisLabels` com 5 h ou 7 d pelo `periodType`).
  Sempre desenha, mesmo com zero anteriores ("sempre tem que mostrar", texto da issue).
- A frase sem reinício deixa de negar a linha: diz que não há janela aberta e que as anteriores estão no gráfico.
- Sem gate de protótipo: o gráfico é o mesmo; muda só o estado vazio. Registro no protótipo e em `presentation.md`.

## Atividades

| ID | O quê | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Diagonal sem janela aberta + frase; protótipo e `presentation.md` no mesmo commit | frontend-dev, prototype-dev |

## Ponto de situação

| ID | Comando | Resultado |
|---|---|---|
| A01 | sem harness (plano); fatos medidos com `sed`/`grep` acima | plano gravado |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"` | `AnalysisPanelModelTest.kt:80` `expected:<62% used; [no open window, the chart shows only the previous ones.]> but was:<62% used; [with no known reset there is no pace line.]>`; idem `:100` (semanal, 41%) |
| A02 | verde: o mesmo comando; `desktopTest --tests "com.usagemonitor.ui.*"`; `allTests koverXmlReport -Pcoverage` | 1216 testes / 0 falhas; 754 / 0; 4476 testes, 0 falhas, 1 ignorado. Protótipo: html.parser sem erro no bloco novo |

## Problemas em aberto e riscos

- Nenhuma janela de host é tocada; risco #342 não se aplica (só modelo puro e o mesmo `Canvas`).
