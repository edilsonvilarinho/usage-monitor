Quota consumption bar. Never the only carrier of the level — the percentage and the status word are always beside it.

```jsx
<AppProgressTrack percent={68} level="warn" label="Sessão 5h" />
```

`info` is for a balance/credit line (Anthropic usage credits), which is measured in currency, not in reset ratio.

**Motion.** Width follows the `GENTLE` spring (critically damped, no rebound past the value) and
the level color crossfades in 180ms, so a collection that moves a quota from 41% to 68% slides
instead of jumping. Both are finite; with "Reduzir animações" the bar swaps in one frame.
