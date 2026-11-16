Chave (legenda) de gráfico: uma linha por série ou região, com amostra no mesmo traço que o gráfico desenha e o rótulo em texto ao lado. Cor nunca informa sozinha: o texto é obrigatório.

```jsx
<AppChartKey items={[
  { label: "seu uso até agora", style: "SOLID", color: lineColor },
  { label: "linha segura: 100 % no reinício", style: "DASHED", color: gold },
]} />
```

**Parâmetros** (`presentation/ui/components/AppChartKey.kt`, interno ao pacote):
- `items: List<AppChartKeyItem>`: `label` (texto, já traduzido pelo model), `style: AppChartKeyStyle`, `color`.
- `modifier`.

Stateless. Itens em coluna, espaçamento `AppSpacing.xs`; cada linha é amostra + texto (`labelSmall`, `onSurfaceVariant`, uma linha).

**Os cinco estilos** (`AppChartKeyStyle`, valor de enum fechado: não criar outro):
- `SOLID`: traço contínuo (linha da janela aberta).
- `DASHED`: tracejado (linha segura, projeção).
- `DOTTED`: pontilhado, cap redondo (marcador "agora").
- `FADED`: traço contínuo com alfa 0.45 (janelas anteriores).
- `ZONE`: faixa preenchida com alfa 0.18 (região "acima" / "abaixo").

**Medidas fixas da amostra** (literais no componente, não são tokens):
- Caixa 18 dp × 8 dp; traço 2 dp.
- Tracejado: 5 dp de traço, 4 dp de vão.
- Pontilhado: traço de 0.1 dp com vão de 3 dp, cap redondo.
- Alfas: `FADED` 0.45; `ZONE` 0.18.

**Cor**: vem do tom do dado (`AppGargantuaTokens.gold`, `AppTone.OK` / `AppTone.CRITICAL`, cor da fonte), nunca literal. Cor de acento sai de `AppAccents.current`.

**Regras**
- Cor nunca sozinha: todo item tem texto ao lado da amostra.
- A amostra usa o mesmo estilo do traço desenhado no gráfico; o model (`PaceLegendEntry`) é a fonte do estilo.
- Sem animação: a amostra não se move.
- Tag de teste da amostra: `appChartKeySampleTag(style)`, valor `appChartKey-sample-<ESTILO>`.

**Quem usa**
- `PaceDiagonalChart` (legenda da diagonal do ritmo, janela de Análise; #610). Os itens vêm de `PaceDiagonalModel.legend` mapeados por `legendToneColor`.

Histórico: #610 (legenda tipada; antes, frases soltas em `Text`).
