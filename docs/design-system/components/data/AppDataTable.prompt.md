Real tabular data. When rows need progress bars, expansion or a source marker, use a list of `AppDataRow` instead.

```jsx
<AppDataTable
  columns={[{ key: 'projeto', label: 'Projeto' }, { key: 'custo', label: 'Custo', numeric: true }]}
  rows={[{ id: 1, projeto: 'api-gateway', custo: 'US$ 3,1841' }]}
/>
```

Zebra striping belongs to the PDF report only — on screen the 1dp divider is enough.
