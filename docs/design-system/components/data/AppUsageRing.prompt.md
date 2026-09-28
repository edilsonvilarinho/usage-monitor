Usage ring for the HUD notch: one arc per quota, concentric, `arcs[0]` drawn outermost. At most three —
OpenCode Go is the largest real case.

```jsx
<AppUsageRing
  arcs={[{ fraction: 0.41, level: 'ok' }, { fraction: 0.68, level: 'warn' }]}
  active
  attentionIndex={1}
  label="Anthropic — Padrão · Atenção · anel externo 7d 41% · anel interno 5h 68%"
/>
```

**The longest window is the outer ring** (issue #278): monthly, then weekly, then the short window,
with balances and credits (no reliable window) innermost; equal windows keep the card order. In API
order the 5h sat outside the weekly, the reverse of how a target reads — the bigger ring is the longer
period. The HUD balloon marks every quota with a 14dp glyph of the same rings with only its own lit,
and `label` names each ring's position ("anel externo", "anel do meio", "anel interno").

**One arc per quota, not one ring per vendor.** Codenotch draws one ring per provider with its worst
window; a single percentage hides the other window — a 7d quota at 100% behind a 5h quota at 12%.

**Never informs alone.** The percentage of the quota in focus (worst risk, then highest percent) and the
status word sit beside it; `label` carries account, word and every quota for assistive tech and tests.
A quota without a forecast has a **dashed** track: no color can suggest a verdict nobody computed.

**Motion.** Each arc follows the `GENTLE` spring — no rebound past the value — and on first composition
draws in from zero (issue #322; reduced motion starts at the value). At rest, behind
`AppMotionPolicy.continuous`, two signals: a 64° white glint with a fading tail runs inside each arc from
start to tip (sine-eased opacity, peak 65%, one pass every 2.8s, inner arcs 22% of a cycle later, arcs
under 6° skipped) — it never passes the tip, where it would suggest a higher percentage; and an 80° band
of light at 22% circles every arc's **track** every 3.6s, 120° apart between arcs, so a ring at 0% moves
too. The track is "what is left", not data, so lighting it suggests no percentage. Arc color changes over `AppMotion.slow`. Two continuous signals,
both only behind `AppMotionPolicy.continuous` (off in tests and capture generators): a thin `--info`
comet orbiting **outside** the rings — 130° whose tail fades to nothing through a sweep gradient, with a
dot at the head, one turn per 2.4s (issue #322; it was a flat 90° segment at 1.4s and read as a loading
spinner) — while a CLI session had a turn in the last 5 minutes (labelled "sessão
ativa", not "processing": the app sees transcripts, not processes), and the arc of the quota in focus
(`attentionIndex`) breathing while the account is at Attention or worse — the arc stays between 0.8 and 1
opacity while a halo twice the stroke, up to 28%, swells and fades over 1.6s with eased ends (it was the
whole arc blinking 0.35↔1 in 0.9s) — not always the outer one, which
would pulse the weekly while the 5h is the critical one. Without the policy the active arc stays drawn, still; the pulse
disappears and the word still says it.

**The orbit sits outside so the mark never shrinks.** Inside the last quota arc it ate the core, and the
provider mark of the account that was working dropped from 14dp to 8dp. It reaches `gap + 0.4 × stroke` to
the orbit line plus the head glow (`1.12 × stroke`) past the ring box (5.3dp at the HUD's 44dp), outside the canvas bounds: whoever places the ring leaves
that much free around it — the notch's 8dp padding and half its 12dp item gap do.

**Static depth** (user feedback on the real HUD: "muito flat, tudo chapado"). Drawn regardless of motion
policy, so tests and captures show it too:
- a soft glow in the arc's own tone under each arc (12% opacity, twice the stroke) — weaker than the
  attention halo, which is still the only one that breathes;
- a **lit tip**: the last 70° ramp to white at 25% and a white bead (85%, 0.28 × stroke radius) sits on the
  arc's end — it marks where the value is and never passes it. A full arc has no tip (start and end meet),
  and arcs under 4° get none;
- a **core well**: a radial disc of `--pressed-layer` (1.4× at the center, fading to zero at the innermost
  arc) behind the provider mark. It is static and ignores `active`: the core never changes with the session;
- the track weighs 2.2× `--pressed-layer` (was 1.6×: at 10% it vanished on the dark notch).

**Active session reads as light, not a hairline.** The comet stroke is 0.8 × stroke (was 0.6 — 1.5dp at the
HUD, lost against the notch); a full **lane** at 14% under it says "this orbits" even when parked; the head
carries a radial `--info` glow of 1.4 × orbit stroke and a white core. The glow is what bounds the orbit now:
`appUsageRingOrbitReach` counts it (5.3dp at the HUD's 44dp), still inside the notch's 8dp padding and half
of its 12dp item gap.

Track: `--pressed-layer` × 2.2. Stroke 3dp, gap 1.5dp, 28dp box (the HUD uses 44dp / 2.5dp / 1.5dp).
