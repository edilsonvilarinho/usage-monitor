The HUD notch — the "Barra HUD" window mode (issue #164, redesigned in the depth-and-motion pass,
balloons and handles in its third round).
Compose: `HudNotch` inside `HudWindowHost`, its own undecorated, transparent, always-on-top window;
the main window is hidden with its geometry intact while it is shown.

```jsx
<AppHudBar edge="right" balloon={0} countdown="02:05" accounts={[
  { label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn', active: true,
    detail: 'Max 20x · via Claude Code',
    quotas: [{ short: '5h', title: 'Sessão 5h', percent: '68%', fraction: .68, level: 'warn',
               reset: '22h59', usedLeft: '68% usado · 32% restante' }] }
]} />
```

**Shape.** Docked to a screen edge (`top`, `bottom`, `left`, `right`): flat and flush on the screen
side, 14dp corners on the inner side, 8dp **concave shoulders** joining the two — it reads as part of
the edge, like the hardware notch Codenotch imitates, not as a pill floating next to it. Exempt from
the 10dp radius ceiling: it is a silhouette, not a panel. Depth `DIALOG`, top sheen, lit border.

**The notch never grows.** Per account in the user's card order (never risk order — the first account
used to swap by itself): an `AppUsageRing` (one arc per quota, up to three), the percentage of the
quota in focus (worst risk, then highest percent) and the **status word — always**. Color never
informs alone. Horizontal on top/bottom, a column on the sides, where a long word ("Sem projeção")
wraps to two lines. **Compact** when the full strip would take more than 45% of the edge (six or
seven APIs on a laptop screen): each account becomes Codenotch's cell — ring and percentage under it,
no word, which stays in the balloon and the ring description. The strip ends with the update icon (no click of its own — #225) and the
countdown to the next collection, **once**: polling is app-wide. Each text estimate carries 1dp of
slack for Skia's whole-pixel rounding, or the countdown breaks into "04:5" at fractional densities.

**Account balloon.** Hovering a ring opens, beside the notch on the inside of the screen, a balloon
for **that account only** — Codenotch's card, not a panel of every account. Header with the provider
mark in the source accent, the card title and the state; per quota the card's title and
"Reinicia 22h59", an `AppProgressTrack` and **"68% usado · 32% restante"** (used truncated like the
ring, left derived from it, "<1%" at both ends, nothing for balances); quotas of one group
(Antigravity models, Cursor allowances) in a box under the group name; the plan and the origin of the
reading, **"Plus · via Codex"**; and the **card's own buttons** (history, CLI sessions, team) plus a
refresh for that account. A curved tail — Codenotch's `TooltipTail` — points at the ring; moving to
another ring slides the balloon on the `GENTLE` spring. Enter: fade + 0.96 scale on `EXPRESSIVE` from
the notch side; exit: 90ms fade. Depth `OVERLAY`. The balloon is window content, never a popup.

**Handles** (Codenotch's `MoveHandle` and `SettingsOrb`). Hovered, a 32dp disc past each end of the
notch: the **hand** at the near end (top or left) moves the notch — drag it, or the notch itself — and
stays composed while carried, bordered in info; the **gear** at the far end opens a balloon with
**everything the standard footer offers**: title with the countdown, the three window modes as rows
(the footer's menu is a popup the HUD window would clip) and the footer's own action row. At rest each
handle is a quarter arc in `outline` inside the shadow margin the resting window already has.

**Size belongs to the geometry** (`hudNotchSizes`): the window is sized before any composition
exists, and measuring there to feed the window would close the resize loop. Estimated from mono
advances — `label*` is Plex Mono — and every balloon row has a fixed height, so the balloon height is
a sum. The open area reserves the **tallest** balloon, so switching rings never resizes the window.
The collapsed width is the max of percentage and word, so a collection that turns `9%` into `88%`
does not resize it either.

**Window, measured.** A click on a transparent pixel of a transparent window is swallowed on Windows
11 — it reaches neither the content nor the window behind. So the window is notch-sized at rest (plus
a 16dp shadow margin on the three inner sides) and grows **in one jump** when the pointer enters,
with the balloon entering inside it; on leave the balloon goes first and the window shrinks after it.
The notch centre is clamped so that notch **and handles** fit, the same way at rest and open: opening
near a corner never moves the notch. Hover is the union of body, balloon and handles.

**Gestures**, one detector: a click on a ring **refreshes that account** (the ring stays pressed while
it collects); right-click goes straight to Cards only; a drag past the touch slop frees the notch, and
on release it docks to the **nearest edge**, saved as edge + fraction along it. Each ring declares its
refresh action in semantics. Ways back to the full window: "Padrão" in the gear balloon, the tray
item, Ctrl+Shift+H.

**Identification.** The provider mark (`AppProviderMark`) sits in the middle of each ring in the
foreground color — the arcs around it already carry the risk colors. The account label is the card
title ("Anthropic — Padrão", never just "Padrão"). Rings are 36dp so the mark fits. The tray icon
tooltip summarises every account with its focus percentage, cut at Windows' 127 characters.
