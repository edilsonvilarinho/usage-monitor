Column captions for a row list. Match the `flex`/`width` of the row's own cells exactly, or the strip lies.

Compose: `AppColumnHeaderLabel` defaults to one line and start alignment. The compact observed HUD
table (#379) passes `maxLines = 2` and `textAlign = TextAlign.End` for its two window captions.
This changes the caption wrapping only: the row and body keep the same column widths.

```jsx
<AppColumnHeader items={[{ label: 'Sessão', flex: 2 }, { label: 'Tokens', flex: 1, align: 'right' }]} />
```
