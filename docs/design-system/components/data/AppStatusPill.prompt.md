Dot + word on a tone-tinted chip — the HUD's per-account state (issue #322). Same anatomy as
`AppStatusIndicator` (6px dot, 4px gap, `labelSmall` in the tone color), plus 6×2px padding, radius 4
(`extraSmall`, never a full pill: it would pass the radius ceiling of 10 and read as a button) and the
tone at **8%** over the surface.

```jsx
<AppStatusPill level="warn">Atenção</AppStatusPill>
<AppStatusPill level="off">Sem projeção</AppStatusPill>
```

- **8%, not more.** The text is written in the tone itself; a fuller tint pulls it below AA. Measured:
  at 14% the light-theme green dropped to 4.17:1; 8% is the highest value where the three tones pass in
  both themes (worst case 4.53:1). `AppStatusPillContrastTest` guards it.
- **The HUD geometry reads its constants** (`STATUS_PILL_PADDING_HORIZONTAL/VERTICAL`, `STATUS_DOT_SIZE`),
  never a copy, plus 1dp of pixel-rounding slack (`HudNotchTextFitTest` found up to 0.6dp at 110–144%).
- Where the state sits next to other numbers of the same weight (the HUD notch) use the pill; in lists,
  cells and card headers `AppStatusIndicator` stays.
- **`rollLabelChanges`** (HUD only): when the word changes it rolls through the Gargantua event
  horizon (D5, `rememberGargantuaRoll(whole = true)`) — the old word rises out in its own tone, the new
  one comes from below with a hot rim. Drawing only; the pill keeps the new word's size. Off everywhere
  else: outside the HUD the pill is not data that updates by itself.
- **Beta channel marker** (issue #355): `BetaReleasePill` is the pill with the word `Beta` in the `warn`
  tone — one owner for the text and the tone. It sits in the release-notes header and next to the footer
  version when the build is a prerelease. The word is always written; the tone never marks beta alone.

```jsx
<AppStatusPill level="warn">Beta</AppStatusPill>
```
