Sub-faixa de um grupo dentro de uma lista — o degrau quieto da escada de superfícies.

```jsx
<AppGroupBand label="Conta · 3f9c…" horizontalPadding={14} indent={24} />
<AppGroupBand
  label="Conta · 3f9c…"
  detail="2 de 5 online · 1 trabalhando agora"
  indent={24}
  trailing={<AppIconButton glyph="×" label="Apagar conta" variant="danger" />}
/>
```

Não é `AppPanelHeader`. Aquele é o cabeçalho de um painel e fala alto — título em `--t12` sobre `--fg`, altura de barra. Esta fala baixo: `--t10` sobre `--muted`, um degrau abaixo da faixa que a cobre e um acima da linha que ela agrupa. Trocar uma pela outra inverte a hierarquia que a escada existe para construir.

`indent` soma ao padding horizontal em vez de substituí-lo: o conteúdo da faixa começa no mesmo x das linhas abaixo dela, deslocado só pelo nível. A divisória é da própria faixa, como a de `AppDataRow` — a lista não leva vão entre itens.
