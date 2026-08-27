Pins the view's parameters to the top of the window.

```jsx
<AppToolbar>
  <AppSegmentedControl items={['5h','7 dias','30 dias','Total']} value={win} onChange={setWin} />
  <AppTextField placeholder="Filtrar projeto ou branch" style={{ maxWidth: 260 }} />
  <span style={{ flex: 1 }} />
  <AppButton variant="ghost">CSV</AppButton>
</AppToolbar>
```
