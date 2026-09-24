The HUD notch — the "Barra HUD" window mode (issue #164, redesigned in the depth-and-motion pass).
Compose: `HudNotch` inside `HudWindowHost`, its own undecorated, transparent, always-on-top window;
the main window is hidden with its geometry intact while it is shown.

```jsx
<AppHudBar edge="top" countdown="02:05" accounts={[
  { label: 'Anthropic — Padrão', statusLabel: 'Atenção', level: 'warn', active: true,
    quotas: [{ short: '5h', percent: '68%', fraction: .68, level: 'warn', reset: '22h59' },
             { short: '7d', percent: '41%', fraction: .41, level: 'ok', reset: 'Ter 21h00' }] }
]} />
```

**Shape.** Docked to a screen edge (`top`, `bottom`, `left`, `right`): flat and flush on the screen
side, 14dp corners on the inner side, 8dp **concave shoulders** joining the two — it reads as part of
the edge, like the hardware notch Codenotch imitates, not as a pill floating next to it. Exempt from
the 10dp radius ceiling: it is a silhouette, not a panel. Depth `DIALOG`, top sheen, lit border.

**At rest**, per account in the user's card order (never risk order — the first account used to swap
by itself): an `AppUsageRing` (one arc per quota, up to three), the percentage of the quota in focus
(worst risk, then highest percent) and the **status word — always**. Color never informs alone, and
at rest there is nothing else on screen. Horizontal on top/bottom, a column on the sides, where a long
word ("Sem projeção") wraps to two lines. The strip ends with the update icon (no click of its own —
#225) and the countdown to the next collection, **once**: polling is app-wide.

**Hovered**, it unfolds on the `EXPRESSIVE` spring toward the inside of the screen: one block per
account (dot + word, name) and one 20dp row per quota — short label, `AppProgressTrack`, percentage,
reset time (#189). A quota with no reset prints nothing in its place, not even a dash. The panel
fades in 60ms after the notch starts growing, so text is never born squeezed.

**Size belongs to the geometry** (`hudNotchSizes`): the window is sized before any composition
exists, and measuring there to feed the window would close the resize loop. Estimated from mono
advances — `label*` is Plex Mono. The collapsed width is the max of percentage and word, so a
collection that turns `9%` into `88%` does not resize the window.

**Window, measured.** A click on a transparent pixel of a transparent window is swallowed on Windows
11 — it reaches neither the content nor the window behind (default, software and OpenGL renderers).
So the window is notch-sized at rest (plus a 16dp shadow margin on the three inner sides) and grows
to the panel size **in one jump** when the pointer enters — the new area is transparent, the jump is
invisible — while the spring runs inside it; on leave the content folds first and the window shrinks
after it settles. No per-frame AWT resize: that was the stutter of the previous strip.

**Gestures**, one detector: a short click opens the full window; right-click goes straight to Cards
only (no popup — it would be clipped inside this window); a drag past the touch slop frees the notch,
and on release it docks to the **nearest edge**, saved as edge + fraction along it (survives a
resolution change). The old pill position migrates once. Move cursor on hover; the click action is
declared in semantics for assistive tech. Three ways back: click, tray item, Ctrl+Shift+H.

**Identification** (pass 2). The provider mark (`AppProviderMark`) sits in the middle of each ring in
the foreground color — the arcs around it already carry the risk colors — and in the source accent
in each expanded block header. The account label is the card title ("Anthropic — Padrão", never just
"Padrão"), and the account plan ("Max 20x", "ChatGPT Plus") follows it in muted text, as in
ai-usagebar's "Claude Max 20x". Rings grew from 28 to 36dp so the mark fits. The tray icon tooltip
summarises every account with its focus percentage, cut at Windows' 127 characters.
