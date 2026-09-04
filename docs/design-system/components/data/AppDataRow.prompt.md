A line of data. Put them directly inside `<AppPanelBody flush>`.

```jsx
<AppDataRow>
  <div style={{ display: 'flex', flexDirection: 'column', gap: 3, flex: 1 }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
      <AppKey>Sessão 5h</AppKey><span style={{ flex: 1 }} /><AppValue>68%</AppValue>
    </div>
    <AppProgressTrack percent={68} level="warn" />
    <AppKey dim>Reinício: Qua 13h00 BRT</AppKey>
  </div>
</AppDataRow>
```

Use `guide` for the child rows of an expanded team member or project — the 2dp stroke sits in the middle of the indent, drawn per row. It rests on `--surface`, never `--bg` — see `guidelines/pattern-nested-guide.html`. `guide` only changes the resting color; a clickable nested row still lifts to `--raised` on hover.

**`<AppValue size="primary">` for the number the row exists to show** — a quota's percentage, a balance. `--t16` is documented in `tokens/typography.css` as "primary metric value", and Compose already renders the quota percentage at that exact size (`MaterialTheme.typography.titleMedium`, `ApiUsageCard.kt`). Default `md` (14) is for a value that is secondary to something else on the same row.
