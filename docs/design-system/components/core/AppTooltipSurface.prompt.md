The bubble anatomy shared by every floating hint in the app: `--raised` background, radius 6, a
1dp border and `--shadow-2`.

```jsx
<AppTooltipSurface style={{ padding: 'var(--s2) var(--s3)', maxWidth: 240 }}>
  <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)' }}>
    A cota deve resetar antes de esgotar.
  </span>
</AppTooltipSurface>
```

**Why it exists.** The anatomy was written out by hand in four places that never had a shared
owner — the plain-text tooltip, the usage card's metric tooltip, the turn chart's hover bubble and
the history chart's hover bubble. Drift between the four is what let two tooltips over the same
chart float at different heights.

**`--raised`, not `--surface`.** `AppMenu` opens on `--surface`; this opens one rung up. A menu is a
list of actions sitting beside the content it acts on; a tooltip is a footnote floating over the
content it explains. The extra rung is what keeps the two reading as different things when they
appear side by side, and it is not an arbitrary choice — it is the same background the Compose
primitive it mirrors already uses (`MaterialTheme.colorScheme.surfaceVariant`, which resolves to
`--raised` in this system's theme mapping).

**Content only.** No default padding, no default max-width. A one-line label, a five-row metric
block and a chart annotation size themselves differently — this component owns only the anatomy,
never the layout of what fills it.

**`--shadow-2`, never `--shadow-8`.** Eight is the dialog and menu elevation, reserved for surfaces
that cover the window; a tooltip covers a point on the screen. Reaching for the heavier shadow here
would make the bubble read as a second, competing layer instead of a footnote.
