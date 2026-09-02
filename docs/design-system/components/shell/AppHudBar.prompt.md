The worst risk across every quota, as a draggable pill that grows on hover to list every source.

```jsx
<AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" resetLabel="reset em 42min" />
<AppHudBar level="ok" dotOnly />
<AppHudBar level="crit" label="Crítico" sourceLabel="Anthropic · Padrão" expanded sources={[…]} />
```

Not a new risk primitive — the dot+word is `AppStatusIndicator`, and the collapsed state reuses
`AppStatusDot`; this pill is only the shell that carries them. No window buttons, no drag-to-reorder
(there is nothing to reorder): the pill is the click target that brings back the full window.

**The pill measures itself from its content, capped at 320dp.** Full width was the first version and
was wrong — always-on-top plus edge-to-edge covered whatever another window had in its own top 24dp
(an IDE's menu bar, an editor's shortcuts), and desktop windows have no partial click-through: the
whole rectangle eats the click whether or not anything is visibly drawn there. A fixed 320dp pill in
one corner was the second version and was still wrong for the same reason at a smaller scale — it
measured 320dp to show the word "Normal". The cap survives so the pill never changes size between
two collections; `sourceLabel`/`resetLabel` truncate with an ellipsis rather than force it wider.

**`dotOnly` is the collapsed state, for when every source is on track.** The data does not vanish —
it stops occupying screen while it says everything is fine, and hover brings the whole pill back.
This is the one place in the system where a dot appears without its word, and it is allowed only
because the word is one mouse movement away. Do not reach for `AppStatusDot` in a list, a cell or a
header: there the indicator with its word is still the right call.

**Hover grows the pill, it does not open a popup.** The source list used to be a `HoverTooltipBox`,
and a popup on this platform is a layer *inside* the window, clipped to its bounds: in a 24dp-tall
window a bubble with a 180dp minimum width and one row per source could not fit — it was clipped
over its own trigger, the pointer landed on the bubble, the trigger got an `Exit`, the tooltip
closed and reopened on the next frame. The list is now content of the window itself, below the
pill, and the window resizes. Two consequences the host must honour:

- **Hover belongs to the whole container, never to the pill alone.** Bound to the top 24dp, moving
  the pointer down into the list would drop the hover, collapse the window and hand the pointer back
  to the pill — the same loop under a different name.
- **The panel row is not `AppDataRow`.** That primitive floors at 32dp plus 8dp of vertical padding,
  and six sources would build a ~288dp panel — a window, not a HUD. Same exception `--h-hud` already
  takes against the 28dp control floor.

**Dragging and clicking are one gesture, split by a movement threshold.** A `clickable` stacked on a
drag detector does not work — the click handler eats the press and the drag never starts. The click
action is *declared* in the semantics rather than installed, so a screen reader keeps the only path
back to the full window. The component emits drag lifecycle callbacks and no coordinates: the host
reads the pointer's absolute screen position, because during a drag the component is moving along
with the window and a local delta would accumulate error.

**Where the pill parks is the user's choice, and it is remembered.** On release it snaps to the
nearest edge of the *work area* — which already excludes the taskbar, so the bottom edge means "just
above it". Drawing over the taskbar is deliberately out of scope: it needs physical screen bounds
and a z-order fight with a window that is also topmost.
