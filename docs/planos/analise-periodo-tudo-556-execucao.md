# Análise: janelas anteriores do período inteiro e período "Tudo" — issue #556

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-11, branch `fix/issue-556-analise-tudo`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes `backend-dev`/`frontend-dev`.

## Objetivo

Na janela "Análise", ao ampliar o período (30 d na captura da issue), o card 5h continua desenhando só 3 janelas
anteriores, enquanto o Histórico mostra várias. O card deve desenhar as janelas do período escolhido, e o seletor
ganha "Tudo", como o Histórico (24 h | 7 dias | 30 dias | Tudo).

## Fatos confirmados (2026-11-11, `3b4c7635`)

- Teto fixo: `ANALYSIS_PREVIOUS_PATHS = 3` (`SessionAnalysis.kt:94`), aplicado com `takeLast` em `windowView`
  (`SessionAnalysis.kt:246`); `windowView` não recebe o período (`sed -n 231,248p`).
- Seletor: `AnalysisPeriod` (`AnalysisPeriod.kt:10`) = NOW, HOURS_24, DAYS_7, DAYS_30, DAYS_90; `DAYS_90` lê
  `HistoryRange.TOTAL` (`SessionAnalysisReader.kt:65-69`).
- Retenção do histórico local: 90 dias (`LocalUsageHistoryDataSource.kt:23`, `retentionPeriod = 90.days`): "Tudo"
  e "90 d" leem o mesmo dado.
- Linhas anteriores com opacidade fixa `PREVIOUS_ALPHA = 0.45f` (`PaceDiagonalChart.kt:48`, uso em `:124`).
- `HistoryRange.TOTAL` reduz a série a 720 pontos (`UsageHistoryRepositoryImpl.kt:117`, `MAX_TOTAL_POINTS_PER_SERIES`).
- Histórico desenha até `HISTORY_WINDOW_ROW_LIMIT` janelas (`HistoryPaceWindows.kt:67`).

## Decisões (do dono, 2026-11-11)

- `DAYS_90` vira `ALL` ("Tudo"/"All"): com retenção de 90 d o dado é idêntico; renomear não cresce o enum
  (regra do `CLAUDE.md`). `ALL` lê `TOTAL` e o período começa na **primeira leitura** da série, não em agora − 90 d:
  com 20 dias de histórico, o ritmo do período não divide por 2160 h.
- Janelas anteriores: todas as fechadas com reinício ≥ início do período (o mesmo corte de `summaryOf`), nunca menos
  que as 3 mais recentes; em `NOW` ficam as 3 de hoje.
- Opacidade das anteriores cai com a quantidade (função pura testada), com piso, para muitas linhas não virarem borrão;
  a legenda diz quantas foram desenhadas.

## Atividades

| ID | O quê | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | `DAYS_90` → `ALL`: início na 1ª leitura, faixa `TOTAL`, rótulos "Tudo"/"All"; protótipo 9d e `presentation.md` | backend-dev |
| A03 | Janelas anteriores do período em `windowView` (piso de 3) | backend-dev |
| A04 | Opacidade por quantidade e contagem na legenda; protótipo 9d e `presentation.md` | frontend-dev |

## Ponto de situação

| ID | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano); fatos medidos com `grep -n`/`sed -n` citados acima | plano criado |

## Problemas em aberto e riscos

- `TOTAL` reduz a série a 720 pontos: em "Tudo" cada janela de 5h fica com poucos pontos e a linha sai mais grossa
  (já era assim em 90 d). Não muda nesta issue.
- Suíte só no Windows (#342); nenhuma atividade mexe em host de janela.
