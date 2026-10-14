The Settings dialog's left rail. Sections: Geral, Alertas, APIs, Contas, Time.

```jsx
<div style={{ display: 'flex', minHeight: 320 }}>
  <AppSettingsNav items={['Geral','Alertas','APIs','Contas','Time']} value={sec} onChange={setSec} />
  <div style={{ flex: 1, padding: 'var(--s4)' }}>{/* only the selected section */}</div>
</div>
```

Mount only the selected section.

**Overflow.** The rail never scrolls with the content pane. When its items outgrow the height,
they scroll inside the rail and the header stays put (#411: Help reached 17 topics, and at the
window's minimum height the last ones were cut off with no way to reach them).

**Motion.** The selected background is one block sliding vertically between sections on the
`SNAPPY` spring; the labels crossfade. The content pane still swaps (only the chosen section is
mounted).
