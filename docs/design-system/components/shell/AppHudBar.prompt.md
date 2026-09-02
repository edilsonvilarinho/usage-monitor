One 20dp row per account: dot and word for its worst quota, then one dot per quota, and the
countdown to the next collection at the end of the first row.

```jsx
<AppHudBar sources={[{ label: 'INFORMATA2', statusLabel: 'Crítico', level: 'crit',
  quotas: [{ text: '5h 28%', level: 'ok' }, { text: '7d 9%', level: 'crit' }] }]} countdown="02:05" />
<AppHudBar level="ok" dotOnly />
<AppHudBar sources={[{ label: 'INFORMATA2', statusLabel: 'Crítico', level: 'crit',
  quotas: [{ text: '5h 28%', level: 'ok', reset: '22h59' },
           { text: '7d 9%', level: 'crit', reset: 'Ter 21h00' }] }]} expanded countdown="02:05" />
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
say what fits in one. (6) One row per quota on hover: the account with a 5h and a 7d window took two
consecutive rows repeating its own name. What stuck is one row per **account**, with one dot per
quota. Each correction came from using it; none was anticipated.

**The per-quota dot without a word has an exact precedent — it is the card's own design.** There a
dot marks each quota and one header badge carries dot *and* word for the worst; here the row's word
plays that badge's part. Color never states anything the row has not already said in writing, and
the word comes from the account's **worst** quota: showing "Normal" with the 7d window blown would
be a lie.

**The window grows interpolated, not in one jump.** Opening the list swapped 24dp for 100dp in a
single frame and read as the bar flickering in size. One pass of the system's normal motion with the
entry easing — a single transition, never a loop, because an endless animation stalls the component
tests' idle wait. Dragging does **not** animate: the bar would trail behind the pointer.

**The chip text uses the quota label's last word** (`Claude 5h` → `5h`): the row already names the
account, so the prefix that tells providers apart is said once, not per quota.

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
and no day-of-month when the window is intraday. Absent means "no reset to show" — nothing is
printed in its place, not even a dash.

**The reset is drawn only while expanded, and beside its own quota.** The resting pill sits on
screen all the time and its rectangle captures the click of whatever is behind it — the complaint
that turned the width into a cap in the first place — so the reset is a detail on demand, and
hovering is already the gesture that reveals the rest of the list. It rides *inside* the quota
block rather than in a column of its own at the end of the row: the row is one per account and the
quotas are several, so a single reset column would have to pick which quota it describes. It is
drawn in the secondary tone with no printed separator: its neighbour is the percentage, which is
consumption, and the tone is what tells them apart — a middle dot between them would spend width
repeating what the tone already said.

**The width cap belongs to the state, not to the component.** The resting cap was calibrated for a
row *without* the reset column; keeping it for the expanded panel would make the new column be paid
for by the account name, which is the mistake the earlier cap raises already refused twice. The
expanded cap is the resting one plus three reset columns — three being the largest quota count on a
single source.

**The countdown is drawn once, on the first row.** The polling is a single loop for the whole app,
not one per account, so repeating it on every row would claim each account has its own collection.
It is absent from the collapsed state, where there is no text at all — hover brings the panel back,
and the countdown with it. On the loading row it stays: that row *is* the first row, and while
nothing has been collected "when is the next attempt" is the most useful thing the strip has to say.

**The icon is what says which time this is.** There is no tooltip to lean on — a popup here is a
layer *inside* the window, clipped to its bounds, and it would land on top of its own trigger — so a
bare `02:05` beside the quota percentages would explain nothing. The sentence rides in the icon's
accessible name, which is also how a screen reader reaches it.

**The reference component takes the value already formatted; the Compose one ticks inside.** That is
a deliberate split, not drift. Here the host is the window composable that builds the whole
application graph, and a per-second state in it would recompose all of it — so the clock lives in the
strip, with the time source and the wait injected, and an explicit switch to turn the loop off. The
switch is not a user preference: under the component tests' clock the wait advances on its own and a
fixed time source never lets the countdown reach zero, so the loop spins forever and the idle wait
never returns.

**The width is measured from a fixed `00:00` placeholder, never from the running clock.** This window
is sized from its content, so measuring the live text would resize it every second. Mono type is what
makes the placeholder honest — every value the strip prints has the same width.

**No new format.** The countdown is the footer's own `mm:ss`, and the sentence behind the icon has a
single owner shared with it: the same countdown said twice would drift apart at the first correction.

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
