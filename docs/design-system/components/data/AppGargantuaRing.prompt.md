Gargantua indicator for the HUD only. A luminous accretion disc and gravitational lens surround a
dark core; up to three concentric quota arcs stay readable outside the scene. Compose:
`AppGargantuaRing`. The generic `AppUsageRing` remains available for other data surfaces.

```jsx
<AppGargantuaRing provider="anthropic" active continuous
  arcs={[{ fraction: .94, level: 'warn' }, { fraction: .51, level: 'ok' }]}
  attentionIndex={0} label="Anthropic — Padrão · 7d 94% · 5h 51% · Atenção" />
```

**Data and scenery have different jobs.** Each quota arc starts at 12 o'clock, clockwise, from
zero to its clamped fraction; it never rotates and never borrows gold from the disc. The track is
a glass tube: a translucent wall with reflections lit from the upper left (strong on the inner rim,
faint on the outer). The value is plasma inside the tube in its own semantic tone: a soft halo, the
tone body and a bright white-tinted core, with the glass reflection passing over it. Three light
pulses may travel along the plasma, fading out near both ends, so no pixel beyond the value ever
changes. A zero quota shows only the empty glass. Outer first,
longest window first, at most three; the balloon keeps every quota. A quota without a forecast has
a dashed track. Preserve the supplied fraction, period, semantic tone and absence of a projection.
Balances and observed activity never become invented quota percentages. The percent and status
word remain beside the indicator, with the existing compact-mode semantics.

**Geometry.** `--gargantua-size: 64px`, stroke 2.5px, gap 1.5px. The larger core keeps provider marks
legible at three quotas. Quota arcs sit outside the disc. The blue active-session comet orbits
outside the outer quota and retains its reserved reach in HUD sizing; it must not be clipped by
the indicator, neighbour, notch or desktop window. The account emoji remains a separate badge.

**Scene — quiet on purpose.** The scene must never compete with the quota arcs or the active-session
comet. A faint warm glow, a large event-horizon shadow (62% of the free core) edged by a thin photon
ring, a thin Einstein lens (upper arc, fainter lower arc) and one translucent flat disc band tilted
-12°: back half behind the shadow, front half across it, lighter on the approaching side (Doppler)
and ember on the receding side. Three faint filaments cross it (integer turns per cycle, so the
loop never jumps). A stronger six-band disc was tried and rejected: it drew the eye away from the
indicator. Gold `#E8AE63`, hot `#FFF0CB`, dust
`#8C643F`, ember `#B4501E` are dedicated decorative tokens. They carry no provider, quota, status or active-session meaning.
The HUD surface keeps the selected theme and its existing surface primitive. No star field, image,
new window backdrop or per-provider palette: the scene is confined to the circular indicator.

**Provider identity.** Reuse `AppProviderMark` in the foreground; its orientation stays fixed.
Keep the account's chosen accent and emoji. On the scene's permanently dark core, use the dark
account-accent variant so the mark remains legible even when the surrounding app uses a light
theme. An unspecified colour uses a light mark. All eleven supported APIs use the same scene;
the existing provider-mark mapping handles the vendor. No vendor-name branching in the renderer.

**Motion.** Only `AppMotionPolicy.continuous && !AppMotionPolicy.reduced` enables continuous
movement. Defaults and captures are static, with the entire scene, identity and values still
visible. Reduced motion also suppresses finite entrances and refresh completion pulses. The
base decorative orbit is 14000ms, the attention breath 3200ms, the active comet 2600ms, and the
disc accelerates to 4000ms while refreshing, the plasma pulses cross each arc in 2800ms. Only
decorative filaments, the plasma pulses and the active comet move; quota arcs keep their length and
anchor and the provider mark never spins. Refresh completion may use the
existing finite provider-mark pulse. Finite quota interpolation never overshoots the real value.

**Accessibility.** The mark and decorative scene are hidden from accessibility. One indicator
description identifies the account, windows, values, state and refresh action; active-session
meaning stays in words in that description or balloon. Disabling motion never hides information.
