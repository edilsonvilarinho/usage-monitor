The HUD notch — the "Barra HUD" window mode (issue #164, redesigned in the depth-and-motion pass,
balloons and handles in its third round).
Compose: `HudNotch` inside `HudWindowHost`, its own undecorated, transparent, always-on-top window;
the main window is hidden with its geometry intact while it is shown.

```jsx
<AppHudBar edge="right" balloon={0} countdown="02:05" version="38.2.0" accounts={[
  { label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn', active: true,
    detail: 'Max 20x · via Claude Code',
    quotas: [{ short: '5h', title: 'Sessão 5h', percent: '68%', fraction: .68, level: 'warn',
               reset: '22h59', usedLeft: '68% usado · 32% restante' }] }
]} />
```

**Shape.** Docked to a screen edge (`top`, `bottom`, `left`, `right`): flat and flush on the screen
side, 14dp corners on the inner side, 8dp **concave shoulders** joining the two — it reads as part of
the edge, like the hardware notch Codenotch imitates, not as a pill floating next to it. Exempt from
the 10dp radius ceiling: it is a silhouette, not a panel. Depth `DIALOG`.

**Body: the event horizon (M1).** In dark themes the body is the black hole's own dark core
(`--gargantua-horizon` `#07080B`, one step above the ring core so each ring's centre still reads) with
no top sheen, and a 2.5dp inner filament of hot light at 8%. In every theme the border is a 1dp
**photon ring with Doppler**, from ember at the bottom-left (the receding side) through gold to hot at
the top-right — the same direction as the ring's disc. Light themes keep the preset surface and sheen;
only the rim changes, in the darker ember/dust/gold so it shows on a light surface. The rim breathes
between 85% and 100% every 6s (`--dur-gargantua-horizon-breath`), only with `continuous && !reduced`;
otherwise it holds frame zero (92.5%). Paint only, inside the same box: nothing resizes. Balloons and
handles keep the theme surface. Chosen among ten HTML options (round M).

**The notch never grows.** Per account in the user's card order (never risk order — the first account
used to swap by itself): an `AppGargantuaRing` (one arc per quota, up to three), **one line per ring with
the window and its percentage** — `7d 72%` over `5h 45%`, outer ring first, `labelSmall` with the
window in `onSurfaceVariant` — and the **status word — always**. A single-quota account keeps the bare
`labelMedium` percentage. It used to be one number, the quota in focus (worst risk), with no window:
the same spot read 45% on one poll and 72% on the next without anything changing (#286). Color never
informs alone. Horizontal on top/bottom, a column on the sides, where a long word ("Sem projeção")
wraps to two lines. **Compact** when the full strip would take more than 45% of the top or bottom edge, or 80% of a
side edge (L1: sides share no titles or tabs, so three accounts stay full on a laptop): each account becomes Codenotch's cell — ring and the focus quota with its
window under it (`7d 72%`), no word, which stays in the balloon and the ring description. The strip
holds **only the accounts**. The countdown to the next collection lives in the **gear balloon**,
on its own line under the title ("Próxima coleta em ◷ 00:25"); the installed version sits on the
title line, right-aligned next to "Usage Monitor", never ellipsized — sharing the countdown line it
had ~50dp and "v41.6.0-beta.2" came out as "v41.6.0…" (#269): with the adaptive cadence (60 s while a CLI session is active) it restarted every
minute on the screen edge. Its icon is a 12dp **draining clock** (a sector that starts full after a
poll and empties clockwise from 12 o'clock) on the **same line** as `mm:ss` (#293); the full turn is
the cadence in force. A bare progress line on the notch border was tried and rejected: without the
number beside it, nobody could tell what it measured. Each text estimate carries 1dp of slack for
Skia's whole-pixel rounding, or the last item of the strip breaks at fractional densities.

**Account balloon.** Hovering a ring opens, beside the notch on the inside of the screen, a balloon
for **that account only** — Codenotch's card, not a panel of every account. Header with the provider
mark in the source accent, the card title and the state; per quota the card's title and
"Reinicia 22h59", an `AppProgressTrack` and, since F10, **"32% restante"** on the left in the text
colour with **"68% usado"** dimmed on the right (used truncated like the ring, left derived from it,
"<1%" at both ends, nothing for balances; screen readers still get one line, "68% usado · 32%
restante"); quotas of one group (Antigravity models, Cursor allowances) in a box under the group
name; a **"Sessões CLI"** `AppBanner` when the account has CLI sessions with a growing or saturated
context or no reply since the last request (issue #265) — the worst signal's tone only on the 2dp
bar, one detail line per signal, "Contexto saturado · 1 sessão", "Sem resposta há 2h10", in the
words of the data and never "Atenção" (the quota risk word) nor "aguardando você" (the app sees
transcripts, not processes); the plan and the origin of the reading, **"Plus · via Codex"**; and the **card's own buttons** (history, CLI sessions, team) plus a
refresh for that account. A curved tail — Codenotch's `TooltipTail` — points at the ring; moving to
another ring (or to the gear) jumps the balloon there and replays the jet from the new ring. Enter and exit are the Gargantua
**relativistic jet (B3)**, the same for the account and the gear balloon: a thin warm beam leaves the
ring centre through the tail and crosses the balloon, which unfolds along the edge from the beam line
(`--dur-gargantua-jet-open` 520ms); closing folds back to the line and pulls the beam into the ring
(`--dur-gargantua-jet-close` 240ms). The box never resizes — the unfold is a clip. Reduced motion:
instant cut. Depth `OVERLAY`. The balloon is window content, never a popup.

**Observed activity (#377).** Zen Free and Kilo Free count requests; Gemini CLI counts tokens.
`observedModels` replaces quotas for these sources: the scene and provider mark remain, with no
quota arcs. The resting/compact line sums all models in the last five hours (`5h 18 req.` or
`5h 12K tok`), and the neutral status is `Atividade local`. **Direction 02, selected for #379:**
the 264dp balloon uses a compact table: model at the left, `Últimas 5h` and `Últimos 7 dias` as
two right-aligned columns, never percentages, reset, remaining or session quota. Title and provider
mark have their own line; the neutral state sits below, so it cannot squeeze the title.
`Requisições observadas` / `Tokens observados` states the unit once. All names and values explicitly
use the selected theme's primary text colour. Model names use mono-12 semibold, wrap to two lines
and expose the full name in the tooltip and accessibility. Numeric cells use mono-10, tabular
alignment and locale thousands separators; the tooltip/accessibility retain the exact count even
when a very large value is ellipsized. Columns share weights .42/.29/.29 and an 8dp gap, with a
12dp scrollbar gutter in both the header and body. Reuse `AppColumnHeaderRow` / `AppDataRow` /
`AppCellValue` in Compose and their web counterparts.
The identity block is 34dp; unit line 17dp; column header 29dp (two 14dp lines plus divider).
At most four 49dp model rows are visible (two 16dp name lines, 8dp vertical padding on each side,
1dp divider), giving a 196dp viewport cap. Only model rows scroll; identity, unit, columns, note
and actions stay fixed. Reduce the viewport to the screen work area at the composition scale.
The note says `Contagem local; limite oficial indisponível`. Accessibility names every model,
window and unit. History, collection and persistence stay unchanged. Kotlin geometry and
composition share these dimensions. With zero activity show a zero count, never a quota percentage.

**Handles** (Codenotch's `MoveHandle` and `SettingsOrb`). Hovered, a 32dp disc past each end of the
notch: the **hand** at the near end (top or left) moves the notch — **only the hand**: dragging the body
moved the notch when the intent was clicking a ring, so a slip on the body just drops the click — and
stays composed while carried, bordered in info. Both handles slide out **from inside the notch** with a
fade and a 0.6 scale on `GENTLE`, and slide back in on exit; the **gear** at the far end opens on **hover**, like a ring (#317) — a click also opens and never closes, since
toggling would close what the pointer just opened — a balloon with
**everything the standard footer offers**: title, the current installed version and the countdown, the three window modes as rows
(the footer's menu is a popup the HUD window would clip), the footer's own action row and, with an
update pending, an `AppBanner` (one-line title, detail on up to two lines) plus the **same action as
the standard update strip** as an `AppButton` ("Reiniciar o app e atualizar", "Baixar atualização";
none while downloading) — offered here, where the label says what the click does, and never on the
notch. At rest each handle is a quarter arc in `outline` inside the shadow margin the resting window
already has.

**Update pending is the gear's dot** (#225, #291). It takes no room in the strip — the old icon was a
phone with an arrow and nobody read it as "new version" at 12dp. At rest the gear's quarter arc takes
the state's tone with a dot on it; open, the gear disc carries an 8dp dot in its corner. The whole
sentence goes into the gear's label: color never informs alone. The collapsed notch has the same size
with and without an update.

**Size belongs to the geometry** (`hudNotchSizes`): the window is sized before any composition
exists, and measuring there to feed the window would close the resize loop. Estimated from mono
advances — `label*` is Plex Mono — and every balloon row has a fixed height, so the balloon height is
a sum. The open area reserves the **tallest** balloon, so switching rings never resizes the window.
The collapsed width is the max of the widest line and the word, so a collection that turns `9%` into
`88%` does not resize it either. Each window adds a 14dp line to the text column: the top-edge content is
64dp with one, two or three windows (the indicator decides) — the state word is an
`AppStatusPill` (issue #322), 18dp tall.

**Window, measured.** A click on a transparent pixel of a transparent window is swallowed on Windows
11 — it reaches neither the content nor the window behind. So the window is notch-thick at rest (plus
a 16dp shadow margin on the three inner sides) and grows **in one jump** when the pointer enters,
with the balloon entering inside it; on leave the balloon goes first and the window shrinks after it.
The notch centre is clamped so that notch **and handles** fit, the same way at rest and open: opening
near a corner never moves the notch. Hover is the union of body, balloon and handles.
**Opening never moves the window's origin along the edge**: at rest it already has the open length
there and only grows inward. A transparent window that changes origin shows one or two frames of old
content at the new place — measured by screen capture, the notch jumped 60px and back on every enter
and leave. The price is the two 38dp strips where the handles appear, transparent and swallowing
clicks at rest too. At the bottom and right edges the origin still moves across (the balloon grows
inward), and one blank frame remains there on open.

**Gestures**, one detector: a click on a ring **refreshes that account** (the ring stays pressed while
it collects); right-click goes straight to Cards only; a drag past the touch slop frees the notch, and
on release it docks to the **nearest edge**, saved as edge + fraction along it. Each ring declares its
refresh action in semantics. Ways back to the full window: "Padrão" in the gear balloon, the tray
item, Ctrl+Shift+H.

**Identification.** The provider mark (`AppProviderMark`) sits in the middle of each ring in the
light foreground on the permanently dark core; a chosen account colour keeps its dark variant.
The arcs around it carry risk colours independently. The account label is the card
title ("Anthropic — Padrão", never just "Padrão"). Indicators are 64dp with a 2.5dp stroke and a
1.5dp gap, reserving a legible core even with three arcs. The tray icon
tooltip summarises every account with its focus percentage **and its window** ("7d 72%"), cut at
Windows' 127 characters — one entry per window would overflow with three accounts.

**Worst window in the tone (issue #322).** In `Atenção`/`Crítico` the percentage of the window that
caused the state is painted in the tone; its window label stays muted, and accounts on track keep every
number neutral. The pill next to it writes the state, so color still never informs alone.

**Mark pulse (issue #322).** When an account's collection finishes, its provider mark scales to 1.15
and back (tween 180ms + 240ms, once). Not continuous, so it does not wait for `continuous`; reduced
motion turns it off.

**Gargantua scene.** `AppGargantuaRing.prompt.md` owns the drawing and motion contract. Its
inclined gold accretion disc, gravitational lens and moving filaments remain inside the indicator;
the existing HUD surface, balloons, account ordering, gestures and API data contracts are
unchanged. All eleven APIs reuse the same renderer with the existing provider mark. Quota arcs
never rotate. Session activity is the blue external comet, with a straight thin **ion tail** (F10) leaving its head backwards, 18° outwards, sized to stay inside the orbit reach. Refresh accelerates the decorative
disc, never the provider logo. A complete static scene is drawn with reduced motion and in tests.
