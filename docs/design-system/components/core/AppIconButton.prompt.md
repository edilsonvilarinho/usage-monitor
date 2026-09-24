Square icon button for card actions (refresh, minimize, history, CLI sessions, team) and window chrome.

```jsx
<AppIconButton glyph="↻" label="Atualizar" onClick={refresh} />
<AppIconButton glyph="–" label="Minimizar card" onClick={minimize} />
<AppIconButton variant="ghost" glyph="▾" label="Recolher integrante" onClick={toggle} />
```

`label` is mandatory: it is both the aria-label and the tooltip, and on the dashboard card it carries the sentence that explains the risk semaphore.

**Press.** Hover → pressed layer (one step above hover) and a scale to 0.96 on the `SNAPPY` spring.
Scale is allowed here because the button carries no text; the dashboard card's action buttons use
the same treatment.
