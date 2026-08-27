repo: edilsonvilarinho/usage-monitor
branch: main

## Last sync

date: 2026-08-26
source: public README read over the web (github_* tools were not connected)

### Updated in this project

- Built the token layer (colors, type, space, shape, motion) from the owner's approved visual prototype.
- Authored 23 reusable primitives across core / forms / data / feedback / shell.
- Recreated eight desktop screens as a click-through UI kit.
- Wrote 21 foundation specimen cards plus the design guide.

## Conformance pass — 2026-08-27

date: 2026-08-27
source: the Kotlin sources, read directly this time

The Compose app was brought onto this system, one activity per surface (issue #117). The token layer
already matched — spacing, shape, elevation, motion, the 10/12/14/16/20/28 ramp and the
Obsidian/Porcelain hexes are identical to `tokens/*.css`. What was missing was **adoption**.

- Six primitives added; see "The conformance pass" in `readme.md`.
- The last Material `Card` left the product.
- `CenteredMessage`, one function drawing loading, error and empty alike in 22 places, was split
  into the three state primitives this system separates.
- `DepthSurface`, a duplicate of the data surface, was removed.
- Suite: 1472 tests, 0 failures.

**Not verified:** nobody has looked at the running window. `gradlew run` was never executed during
the pass.

## Screen map

| Screen | Built from |
| --- | --- |
| Dashboard | prototype §4 + §4b/§4c; README "Dashboard", "Integracoes suportadas" |
| Cards-only mode | prototype §4 "Modo somente cards" |
| History | prototype §5; README "Historico" |
| CLI Sessions (+ Resumo, Tendência) | prototype §6 / §8; README "Sessoes CLI" |
| Session detail | prototype §7 |
| Team usage / trend | prototype §9 / §9b; README "Integracao com time" |
| Presence | prototype §10 / §10b |
| Settings | prototype §12–§12e; README "Preferencias persistidas" |
