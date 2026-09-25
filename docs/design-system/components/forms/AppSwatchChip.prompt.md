A colour option: a round swatch and a label, **one choice among several**. Built for the colour of
each Claude account (issue #275) in Settings → Accounts.

```jsx
<AppSwatchChip label="Padrão" swatch={null} selected={!color} onClick={() => setColor(null)} />
<AppSwatchChip label="Violeta" swatch="var(--acct-violet)" selected={color === 'violet'} onClick={() => setColor('violet')} />
```

**Not an `AppToggleChip`.** A toggle chip switches one restriction on or off; this picks one of N. In
Compose it is `selectable` with `Role.RadioButton`, which is what `assertIsSelected` observes.

**The selected option carries a mark besides the highlight** (`✓`), and the mark's slot is reserved in
every option: colour never informs alone, and without the reserved slot the label would shift sideways
on every change. `swatch={null}` is the "Default" option, drawn as an empty ring because it has no
colour of its own — it means "the vendor accent".

The palette behind it is fixed, not a free picker: every colour has a dark and a light variant with AA
contrast measured in `AppAccentsContrastTest`. A free colour picked in the dark theme would have no way
to pass on the light surface.

Swatch 10dp, mark slot 10dp, `CONTROL_HEIGHT` minimum height, `--r1` radius.
