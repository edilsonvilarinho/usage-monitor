Switches what a window is showing. Use `AppSegmentedControl` instead when the choice is a *parameter* of the same content.

```jsx
<AppTabs items={['Sessões', 'Resumo', 'Tendência']} value={tab} onChange={setTab} />
```

**Motion.** One underline for the whole bar, sliding from the old tab to the new one on the
`SNAPPY` spring (no rebound), while the labels crossfade color in 180ms. The first position is a
jump, not a slide — an underline gliding in from the left every time a window opens describes no
change. It lives in a `Box` that wraps only the tab row, so the tabs' published positions and the
underline share one origin; a full-width `Box` under each label stretches the column and swallows
every click.
