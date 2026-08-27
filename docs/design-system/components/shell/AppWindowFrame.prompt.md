Wraps any full screen in this system — dashboard, history, CLI sessions, team, settings.

```jsx
<AppWindowFrame title="Usage Monitor" dense footer={<AppStatusBar left={…} right={…} />}>
  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s3)' }}>…</div>
</AppWindowFrame>
```

`dense` for the dashboard only; every other window opens wide and uses `--s4`.
