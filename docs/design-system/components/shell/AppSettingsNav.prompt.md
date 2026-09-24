The Settings dialog's left rail. Sections: Geral, Alertas, APIs, Contas, Time.

```jsx
<div style={{ display: 'flex', minHeight: 320 }}>
  <AppSettingsNav items={['Geral','Alertas','APIs','Contas','Time']} value={sec} onChange={setSec} />
  <div style={{ flex: 1, padding: 'var(--s4)' }}>{/* only the selected section */}</div>
</div>
```

Mount only the selected section.

**Motion.** The selected background is one block sliding vertically between sections on the
`SNAPPY` spring; the labels crossfade. The content pane still swaps (only the chosen section is
mounted).
