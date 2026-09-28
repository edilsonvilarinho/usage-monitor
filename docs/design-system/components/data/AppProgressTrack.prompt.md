Quota consumption bar. Never the only carrier of the level — the percentage and the status word are always beside it.

```jsx
<AppProgressTrack percent={68} level="warn" label="Sessão 5h" />
```

**Pace marker** (`marker`, issue #327). Optional 2dp line across the full 4dp track, at the share of
the quota window already elapsed — where the fill would sit if the quota were used at a constant pace
until the reset. Fill past the marker means ahead of pace. It is painted over the fill, never animated
(it is the clock), and it is omitted when the source does not report the window start or has no reset
(prepaid balance). The marker is position only: the row states it in text through the quota tooltip
("Janela decorrida: 42%") and the track's accessible state description.

```jsx
<AppProgressTrack percent={68} level="warn" marker={42} label="Sessão 5h" />
```

`info` is for a balance/credit line (Anthropic usage credits), which is measured in currency, not in reset ratio.

**Motion.** Width follows the `GENTLE` spring (critically damped, no rebound past the value) and
the level color crossfades in 180ms, so a collection that moves a quota from 41% to 68% slides
instead of jumping. Both are finite; with "Reduzir animações" the bar swaps in one frame.
