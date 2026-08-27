Square icon button for card actions (refresh, minimize, history, CLI sessions, team) and window chrome.

```jsx
<AppIconButton glyph="↻" label="Atualizar" onClick={refresh} />
<AppIconButton glyph="–" label="Minimizar card" onClick={minimize} />
<AppIconButton variant="ghost" glyph="▾" label="Recolher integrante" onClick={toggle} />
```

`label` is mandatory: it is both the aria-label and the tooltip, and on the dashboard card it carries the sentence that explains the risk semaphore.
