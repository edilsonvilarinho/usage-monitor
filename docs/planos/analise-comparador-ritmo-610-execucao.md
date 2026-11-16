# Modal de Análise legível: comparador de ritmo — issue #610

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-16, branch
`feat/issue-610-analysis-pace-comparator`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O card de cada janela no modal de Análise é difícil de ler (issue #610, `prototipos/issue-610/captura-issue.png`):
legenda em frases sem amostra, "N janelas anteriores" sobrepondo a grade, rótulo e valor com o mesmo peso, frase de
veredito repetindo a pílula. Direção escolhida pelo usuário: **P05 — Comparador de ritmo**
(`prototipos/issue-610/p05-comparador-ritmo/index.html`, captura `qa.png`).

## Fatos confirmados (2026-11-16, `53f0a234`; varredura do explorer 610-X2)

- Legenda é `List<String>` sem estilo (`PaceDiagonalModel.kt:47-48`, montada em `:104-111`), desenhada como um `Text`
  por entrada (`PaceDiagonalChart.kt:198-201`). O estilo de cada traço existe só no desenho (`PaceDiagonalChart.kt:112-135`).
- Nenhuma primitiva desenha amostra tracejada/pontilhada; legendas locais (`HistoryChartLegend`, `TrendLegendEntry`,
  `TurnSeriesLegend`) só têm traço contínuo.
- `AppProgressTrack(fraction, tone, modifier, marker: Float?)` (`components/AppStates.kt:183-188`); o contrato
  (`docs/design-system/components/data/AppProgressTrack.prompt.md:7-12`) define o `marker` como relógio da janela.
- Fatos do card em `AnalysisFactCell` próprio (`AnalysisPanel.kt:120-139`); `AppDataRow` existe (`AppStructure.kt:471`).
- Tamanhos: `AnalysisPanel.kt` 167, `AnalysisPanelModel.kt` 307, `PaceDiagonalChart.kt` 293, `PaceDiagonalModel.kt` 214 linhas.
- Testes existentes: `AnalysisPanelModelTest.kt:31-105`, `PaceDiagonalModelTest.kt:17-61`, `AnalysisScreenTest.kt:79-110`,
  `PaceLabelPlacementTest.kt:42-45`. Mockup: `docs/planos/prototipo-visual-opencode.html:2173-2280` (seção 9d).

## Decisões

- **D1 (usuário):** criar a primitiva `AppChartKey` (stateless, em `presentation/ui/components/`), com contrato
  `docs/design-system/components/data/AppChartKey.prompt.md`. Item = amostra + texto; amostras: `SOLID`, `DASHED`,
  `DOTTED`, `FADED` (traço com alfa) e `ZONE` (bloco preenchido com alfa). Cor nunca sozinha: sempre texto. Criada e
  adotada no mesmo commit.
- **D2 (usuário):** ampliar o contrato do `AppProgressTrack`: `marker` é uma referência na mesma escala da barra (relógio
  da janela na cota; ritmo seguro na Análise); o texto ao lado diz qual. Sem mudança de assinatura.
- **D3:** comparador de ritmo — linhas "Média" e "Última hora", barra `AppProgressTrack` na escala comum, `marker` no
  ritmo seguro, valor em mono à direita e palavra "acima do seguro"/"abaixo do seguro" (cor nunca sozinha). Escala =
  teto do maior dos três ritmos no passo 1-2-3-5 × 10^k (29 → 30; 2,7 → 3). Eyebrow "Ritmo · mesma escala · 0 a N %/h".
  Ritmo `null` (não medido) mostra "—" sem barra; sem ritmo seguro, sem marca e sem palavra.
- **D4:** fatos viram lista `AppDataRow` (rótulo à esquerda, valor mono à direita): Uso ("49 % · 34 % do tempo"),
  Decorrido, Reinício, Esgota (só quando esgota: "ter 13:19 · 5 d 4 h antes"), Janelas anteriores. Ritmos saem da lista
  (estão no comparador). "N janelas anteriores" sai do gráfico e da legenda solta: fica na chave (amostra `FADED`) e na lista.
- **D5:** frase de veredito curta, sem repetir a pílula: cabe → "Projeção de 85 % no reinício, abaixo dos 100 %.";
  esgota → "Esgota ter 13:19, 5 d 4 h antes do reinício.".
- Texto novo por `localized(…)`; nenhum `== AppLanguage.PT` novo. Nenhum valor novo em enum existente.

## Atividades

| Id | Atividade | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | `AppChartKey` (D1) + legenda com estilo por entrada no `PaceDiagonalModel` + adoção no `PaceDiagonalChart`; contrato, mockup 9d | frontend-dev |
| A03 | Comparador de ritmo (D2, D3) no modelo e no card; contrato do `AppProgressTrack`; mockup 9d | frontend-dev |
| A04 | Lista de fatos `AppDataRow` (D4) e frase curta (D5); mockup 9d e `docs/presentation.md` | frontend-dev |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `explorer` 610-X2 (somente leitura) | fatos acima; escolha P05 e D1/D2 pelo usuário em 2026-11-16 |
| A02 | Vermelho (stub com todo estilo `SOLID`): `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.PaceDiagonalModelTest"` | `PaceDiagonalModelTest.kt:28 expected:<[… style=DASHED …]> but was:<[… style=SOLID …]>` |
| A02 | Verde: `desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*Analysis*" --tests "com.usagemonitor.ui.*Pace*" --tests "com.usagemonitor.ui.*ChartKey*"` | exit 0; `PaceDiagonalModelTest` 3/3, `AppChartKeyTest` 1/1, `PaceLabelPlacementTest` 4/4 |
| A03 | Vermelho (stub do modelo): `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.AnalysisPanelModelTest"` | `:235 expected:<[Ritmo · mesma escala · 0 a 20 %/h]> but was:<[]>`; `:194 expected:<30.0> but was:<0.0>`; `:216 expected:<[acima do seguro, abaixo do seguro]> but was:<[null, null]>` |
| A03 | Vermelho de tela (comparador fora do card): `desktopTest --tests "com.usagemonitor.ui.AnalysisScreenTest"` | `AssertionError at AnalysisScreenTest.kt:114` (nó do comparador ausente) |
| A03 | Verde: `desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*Analysis*"` | exit 0; `AnalysisPanelModelTest` 16/16, `AnalysisScreenTest` 7/7 |
| A03 | Decisões da entrega; mockup 9d por captura headless | ritmo igual ao seguro = "abaixo do seguro"; sem ritmo positivo, escala 1 %/h; marca é posição relativa; mockup com comparador nos cards e zonas na chave |
| A04 | Vermelho (asserção): `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*Analysis*"` antes da implementação | `AnalysisPanelModelTest.kt:40 expected:<[]Projeção de 83 % no ...> but was:<[Cabe até o reinício: usa o ritmo da última hora (14 %/h). ]Projeção de 83 % no ...>`; `:73 expected:<Runs out [Tue 13:46, 14 min before the rese]t.> but was:<Runs out [before the reset: uses the last hour's pace (30 %/h)...]t.>` |
| A04 | Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*Analysis*"` | exit 0; 1229 testes, 0 falhas (XML de `desktopTest`); `AnalysisPanelModelTest` 16/16; `AnalysisScreenTest` 7/7 |
| A04 | Decisões: fatos na ordem D4 em `AppDataRow`; "30 d" da tela usa `performScrollTo` antes do `assertIsDisplayed` (o `Box` de 860 dp é o viewport do `verticalScroll`, subir a cena não o altera); Uso sem espaço antes do % ("62% · 70% do tempo"), o briefing citava "49 % · 34 %" | Protótipo 9d não editado (outro agente); divergência de formato para o orquestrador |
| A02 | Decisões da entrega | linha segura em `AppGargantuaTokens.gold` (como o gráfico); legenda inclui as zonas acima/abaixo (`ZONE`); medidas da amostra fixas e registradas no contrato |

## Problemas em aberto e riscos

- `AnalysisScreenTest` afirma textos da legenda antiga (`:103-104`); A02 os atualiza junto com o comportamento.
- Rótulos "agora"/"esgota" encostando no marcador semanal foram vistos nos protótipos P05/P07; a colocação atual em
  Compose (`PaceLabelPlacementTest`) não muda neste plano.
