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

**Não é o mesmo aninhamento de `guidelines/pattern-nested-guide.html`.** As duas respondem perguntas
diferentes: esta rotula um grupo que **não tem linha própria** (uma conta, acima dos integrantes que
ela agrupa — o grupo em si nunca aparece como dado, só como cabeçalho). O guia aninhado marca filhos
de uma linha-pai que **já é uma linha de dado completa** (uma sessão que expande em turnos). Por isso
as cores divergem por design: esta faixa fica em `--surface`; o bloco aninhado do guia também está em
`--surface` (correção de 2026-09-04, issue #223 — a régua era `--bg` e nunca bateu com
`Modifier.appNestedGroupItem`, a implementação real), mas chega lá por outro caminho — a régua de três
degraus é faixa em `--raised`, linha-pai transparente, filhos em `--surface`.
