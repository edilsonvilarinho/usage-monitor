One line at rest; one 20dp row per monitored quota on hover.

```jsx
<AppHudBar topLine={{ statusLabel: 'Crítico', level: 'crit', label: 'Padrão', quotaSummary: '5h 88% · 7d 9%' }} />
<AppHudBar level="ok" dotOnly />
<AppHudBar topLine={TOP} sources={QUOTAS} expanded />
```

Not a new risk primitive — the dot+word is `AppStatusIndicator`, and the collapsed state reuses
`AppStatusDot`; this panel is only the shell that carries them.

**Five content versions, four corrected after using it.** (1) A single line with the worst
source only: with several accounts monitored, the others had no signal they existed. (2) The others
behind a hover tooltip: the data sat behind a gesture, and the popup flickered — a popup on this
platform is a layer *inside* the window, clipped to its bounds, so in a 24dp-tall window a bubble
with a 180dp minimum width landed on top of its own trigger, the pointer moved onto the bubble, the
trigger got an `Exit`, and it closed and reopened every frame. (3) The list with no consumption at
all: quota is the provider's ceiling, and what the machine spent appeared nowhere. (4) One line per
*source*, carrying that source's worst quota: an account with both a 5h and a 7d window still showed
a single limit. (5) One row per *quota* plus a spend footer, always visible: ten rows on screen to
say what fits in one. What stuck joins the two halves that were right — the summary fits one line,
the detail is one mouse movement away. Each correction came from using it; none was anticipated.

**At rest the row is a summary, not the first list row.** It carries the source once and then every
quota percentage of that source (`5h 88% · 7d 9%`), using the quota label's **last word**: the row
shows a single source, so the prefix that tells providers apart is already said by the account name
beside it. The word and tone come from that source's *worst* quota — showing "Normal" with the 7d
window blown would be a lie.

**The order is the user's card order, never the risk order.** With risk deciding, the resting line
changed account on its own and you never knew in advance who was on it.

**A quota with no forecast still gets its line**, with a neutral dot and a word saying so. The
percent is measured fact and does not depend on a projection — that is the difference from the
card's badge, which disappears without one: there the question is "what state", here it is "how much
already". Under the badge's rule, sources that never produce a forecast would vanish from the HUD
entirely. In the ordering, "no forecast" sorts *after* on-track: a known normal informs more than an
unknown. Collapsing to the dot requires a forecast on every quota — with one missing, "everything is
fine" would be a guarantee nobody gave.

**Every row carries dot AND word.** The percent beside it describes *consumption*, not risk — 40% at
eleven in the morning can be worse than 80% ten minutes before the reset, and the word is what says
which. Drop it and color would be informing state alone, which this system does not do.

**No new formats in the row.** The percent is the card's own (truncated, never rounded). The short
reset comes from the *same* date parts the card's line uses, just trimmed: no prefix, no timezone,
and no day-of-month when the window is intraday. Absent means "no reset to show" — the column
disappears rather than printing a dash.

**`dotOnly` is the idle state, for when every source is on track.** The data does not vanish — it
stops occupying screen while it says everything is fine, and hover brings the whole panel back. This
is the one place in the system where a dot appears without its word, and only because the word is
one mouse movement away.

**The panel row is not `AppDataRow`.** That primitive floors at 32dp plus 8dp of vertical padding,
and six sources would build a ~288dp panel — a window, not a HUD. Same exception `--h-hud` already
takes against the 28dp control floor.

**Hover belongs to the whole container, never to one row.** Bound to a single line, moving the
pointer into the panel would drop the hover and collapse the window under it.

**Dragging and clicking are one gesture, split by a movement threshold.** A `clickable` stacked on a
drag detector does not work — the click handler eats the press and the drag never starts. The click
action is *declared* in the semantics rather than installed, so a screen reader keeps the only path
back to the full window. The component emits drag lifecycle callbacks and no coordinates: the host
reads the pointer's absolute screen position, because during a drag the component is moving along
with the window and a local delta would accumulate error.

**Where the panel parks is the user's choice, and it is remembered.** On release it snaps to the
nearest edge of the *work area* — which already excludes the taskbar, so the bottom edge means "just
above it". Drawing over the taskbar is deliberately out of scope: it needs physical screen bounds
and a z-order fight with a window that is also topmost. The anchor always describes the full panel,
even while the dot is what is on screen; anchoring on the dot would make the window jump every time
a source left the on-track state.
