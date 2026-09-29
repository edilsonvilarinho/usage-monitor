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

**Refresh (R1, gravitational waves).** While an account is being fetched, three thin staggered
waves leave the ring every 1300ms (`--dur-gargantua-ripple`) and the disc speeds up to 4000ms;
continuous, so only with `continuous && !reduced`. When the fetch ends (also a failed one) the
plasma slides from the old value to the new one and one stronger wave plays once
(`--dur-gargantua-refresh-wave`, 800ms); finite, so only Reduced motion removes it. The ring is no
longer pressed and the mark no longer spins or pulses.

**New data (D5, event horizon).** When a strip line ("7d 56%" → "7d 61%") or the state word changes,
only the characters that changed roll like an odometer: the old one rises and disappears, the new one
comes from below, and the base of each flashes a thin hot rim (`--dur-gargantua-roll` 480ms,
`--dur-gargantua-roll-stagger` 50ms, standard curve, never an overshoot). The label ("7d ") never
rolls; the rest is compared from the right. Drawing only, over the new value's text — the HUD
geometry is unchanged. Not on first composition, not with Reduced motion.

**Birth and collapse.** When an API is enabled, and when the app starts or the HUD opens, each
indicator is born (S1, shockwave, `--dur-gargantua-birth` 1100ms, 140ms cascade between accounts):
a point of light flashes, two shockwaves travel out to about 3dp past the ring, the horizon opens
from the centre, the plasma fills up to the value (never past it), then the mark and the text fade
in. When an API is disabled the indicator collapses (C2, `--dur-gargantua-collapse` 480ms): the
text leaves first, the plasma retracts, the ring shrinks to a point and ends in a small flash. The
item keeps its slot until the collapse ends; only then does the notch shrink, in one step. Both are
finite transitions: Reduced motion makes them an instant cut. Chosen from HTML prototypes (3 births,
4 collapses) by the user.

**Balloon (B3, relativistic jet).** The HUD balloon of an account and of the gear opens with a thin
beam — white at the ring centre, hot in the middle, gold fading at the tip, 1.6dp with a wide faint
halo and an 8dp flash at the origin — that crosses the balloon; the balloon unfolds along the edge
from the beam line (`--dur-gargantua-jet-open` 520ms). Closing folds it back and retracts the beam
(`--dur-gargantua-jet-close` 240ms). Moving to another ring or to the gear replays the opening from
the new ring. Data never animates: the bars keep their length, only the clip
grows. Chosen from 5 HTML prototypes by the user.

**Accessibility.** The mark and decorative scene are hidden from accessibility. One indicator
description identifies the account, windows, values, state and refresh action; active-session
meaning stays in words in that description or balloon. Disabling motion never hides information.
