Real tabular data. When rows need progress bars, expansion or a source marker, use a list of `AppDataRow` instead.

```jsx
<AppDataTable
  columns={[{ key: 'projeto', label: 'Projeto' }, { key: 'custo', label: 'Custo', numeric: true }]}
  rows={[{ id: 1, projeto: 'api-gateway', custo: 'US$ 3,1841' }]}
/>
```

Zebra striping belongs to the PDF report only — on screen the 1dp divider is enough.

Compose tables use `AppColumnHeaderRow`, `AppDataRow` and `AppCellValue`. `AppCellValue` keeps
mono-12 and start alignment by default; `style` and `textAlign` allow the compact HUD (#379) to
use the existing mono-10 step and align counts to the right. Colour still defaults to `onSurface`.
