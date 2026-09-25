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

**Motion.** Each arc follows the `GENTLE` spring — no rebound past the value. Two continuous signals,
both only behind `AppMotionPolicy.continuous` (off in tests and capture generators): a thin `--info`
arc orbiting **outside** the rings while a CLI session had a turn in the last 5 minutes (labelled "sessão
ativa", not "processing": the app sees transcripts, not processes), and the arc of the quota in focus
(`attentionIndex`) pulsing while the account is at Attention or worse — not always the outer one, which
would pulse the weekly while the 5h is the critical one. Without the policy the active arc stays drawn, still; the pulse
disappears and the word still says it.

**The orbit sits outside so the mark never shrinks.** Inside the last quota arc it ate the core, and the
provider mark of the account that was working dropped from 14dp to 8dp. It reaches `gap + 0.6 × stroke`
past the ring box (3dp at the HUD's 36dp), outside the canvas bounds: whoever places the ring leaves
that much free around it — the notch's 8dp padding and half its 12dp item gap do.

Track: `--pressed-layer`. Stroke 3dp, gap 1.5dp, 28dp box.
