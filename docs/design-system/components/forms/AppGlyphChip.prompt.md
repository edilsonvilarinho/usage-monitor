A glyph option: an emoji (or a short word) with no label, **one choice among several**. Built for the
emoji of each Claude account (issue #287) in Settings → Accounts, next to the colour picker.

```jsx
<AppGlyphChip description="Nenhum" selected={!emoji} onClick={() => setEmoji(null)}>Nenhum</AppGlyphChip>
<AppGlyphChip description="Maleta" selected={emoji === 'briefcase'} onClick={() => setEmoji('briefcase')}>💼</AppGlyphChip>
```

**The contract of `AppSwatchChip`, without the label.** Radio semantics (`selectable` with
`Role.RadioButton` in Compose), a `✓` mark besides the highlight, and the mark's slot reserved in
every option so the glyph does not shift on change. Sixteen options with their names written would
take three rows of the tab, so the name moves to `description`, which is the option's
`contentDescription`: screen readers and the component tests reach it there.

The glyph is a slot, not a string: Compose passes `AccountEmojiGlyph`, which sizes the emoji in dp so
the system font scale cannot push it out of its box. The "None" option passes a word in `label*`.

Mark slot 10dp, 2dp between glyph and mark, `CONTROL_HEIGHT` minimum height, `--r1` radius.
