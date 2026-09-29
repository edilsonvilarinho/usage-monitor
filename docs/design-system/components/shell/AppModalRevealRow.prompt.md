How every modal opens and closes: **E9, plasma filaments** (round E of the visual-options skill,
chosen among ten). Compose: `Modifier.appModalRevealRow()` + `ModalRevealState`
(`GargantuaModalFilaments.kt`), driven by `AppDialogWindow` and by the `AppDialog` card.

```jsx
{rows.map((row, i) => (
  <AppModalRevealRow key={row.id} index={i} count={rows.length}>
    <AppDataRow>{row.cells}</AppDataRow>
  </AppModalRevealRow>
))}
```

**Opening (`--dur-gargantua-filament-open`, 680ms).** The window (or the dialog card) fades in within
~100ms. Each marked row starts `0.5 / count` after the previous one, from 8% in; a filament (ember
tail, gold body, hot head with a small point of light) runs under the row's bottom edge, and the row
is revealed left to right behind the head within 35% of the clock. The filament fades right after.

**Reading order comes from position, never from composition**: top, then left, by the box each row
publishes. The settings side navigation is composed before the content and would otherwise go first
as a whole. Exact ties fall back to arrival order, so the order is total and deterministic.

**Closing (`--dur-gargantua-filament-close`, 220ms).** Rows retract right to left, last row first;
the window fades only in the last 15%.

**Data never animates wrong.** The row is in its final place from the first frame and is only
clipped: no bar grows, no number counts, nothing overshoots. The filament is scenic gold and never
encodes consumption or state.

**Who marks.** The row primitives already do: `AppDataRow`, `AppSectionHeader`, `AppToolbar`,
`AppColumnHeaderRow`, `AppGroupBand`, `AppMetricBlock`, `AppStatusBar`, `AppBanner` and the
`AppSettingsNav` items. A screen marks only a block that goes through none of them (a chart, the Help
demo). Unmarked content appears with the frame. **Never nest** a marked row inside another: it would
be clipped twice. Outside a modal (`LocalModalReveal` is null: the HUD, its balloons, cards) the
modifier does nothing.

**Replay on content change** (`AppModalRevealScope(replayKey)`). A scope plays E9 again on its own
rows when it is first composed with the window already settled, and every time its key changes:
Settings section, Help topic, data arriving after the open and list ↔ detail (every
`AppStateCrossfade` target is a scope), tabs, time range, quota and account (History, CLI and Codex
sessions, team usage), sub-tab, sort and page (the breakdown). Keys come from the **loaded** data
(`rememberSettledRevealKey`), never from the click, or the replay would run over the stale faded
content and again on arrival. Live ticks and typed filters never replay. States form a chain: a row
registers with its scope and the window; a moving window (open/close) wins, otherwise the innermost
scope that is playing.

**Reduced motion and non-Windows**: no filaments, the window opens and closes at once, and no scope
replays (`replayEnabled` follows the window's own decision).
