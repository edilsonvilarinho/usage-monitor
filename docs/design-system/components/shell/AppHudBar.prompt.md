The worst risk across every quota, one 24dp line pinned to the top of the screen.

```jsx
<AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min" />
<AppHudBar level="ok" label="Normal" />
```

Not a new risk primitive — the dot+word is `AppStatusIndicator`; this strip is only the shell that
anchors it to the screen edge. No drag-to-reorder (there is nothing to reorder), no window buttons:
the whole line is the click target that brings back the full window.
