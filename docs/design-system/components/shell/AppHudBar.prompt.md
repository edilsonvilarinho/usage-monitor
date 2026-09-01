The worst risk across every quota, a 320×24dp pill docked to the top-right corner of the screen.

```jsx
<AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min" />
<AppHudBar level="ok" label="Normal" />
```

Not a new risk primitive — the dot+word is `AppStatusIndicator`; this pill is only the shell that
anchors it to the screen corner. No drag-to-reorder (there is nothing to reorder), no window buttons:
the whole pill is the click target that brings back the full window.

**Full width was the first version, and it was wrong.** Always-on-top plus edge-to-edge covered
whatever another window had in its own top 24dp — an IDE's menu bar, an editor's shortcuts. Desktop
windows have no partial click-through: the whole rectangle eats the click whether or not anything is
visibly drawn there. A fixed-width pill in one corner is the fix — found live, not anticipated. The
component itself doesn't know its own width; it fills whatever the host gives it (`sourceLabel`/
`resetLabel` truncate with an ellipsis, they never force the container wider).
