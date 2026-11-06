Column captions for a row list. Match the `flex`/`width` of the row's own cells exactly, or the strip lies.

Compose: `AppColumnHeaderLabel` defaults to one line and start alignment. The compact observed HUD
table (#379) passes `maxLines = 2` and `textAlign = TextAlign.End` for its two window captions.
This changes the caption wrapping only: the row and body keep the same column widths.

```jsx
<AppColumnHeader items={[{ label: 'Sessão', flex: 2 }, { label: 'Tokens', flex: 1, align: 'right' }]} />
```

## Coluna ordenável (`AppSortableColumnHeaderLabel`, #537)

Legenda clicável que ordena a lista. Arquivo próprio: `presentation/ui/components/AppSortableColumnHeader.kt`.
Quem ordena é o dono do estado (`remember` na tela); a primitiva é stateless.

- `sortDescending`: `null` = coluna inativa (rótulo `onSurfaceVariant`, sem seta; seta fraca só no hover);
  `true` = ativa, descendente (rótulo `onSurface`, seta para baixo); `false` = ativa, ascendente (seta para cima).
- Largura: a seta fica fora do fluxo, no vão à direita do rótulo (deslocada por `AppSpacing.xs`). Não entra na
  largura da célula: ativar a coluna não muda a largura da legenda. Rótulo não trunca nas larguras reais da tela.
- Semântica: `Role.Button`; `stateDescription` recebe `descendingStateDescription` ou `ascendingStateDescription`
  conforme a direção, textos vindos de quem chama (`localized`). Cor e seta nunca informam sozinhas.
- Sem cor de acento: acento é identidade de fonte.
- Movimento: só a cor do rótulo e da seta, por `appTween` com `AppMotion.fast` (respeita `LocalAppMotionPolicy`).
  A reordenação da lista é corte seco.
