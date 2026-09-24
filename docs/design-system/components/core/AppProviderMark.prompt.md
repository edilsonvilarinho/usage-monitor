Provider identification mark: the Claude asterisk, the OpenAI knot on Codex, the Cursor cube, the
Antigravity arch, the Gemini spark, DeepSeek, MiniMax, OpenRouter, Kilo and OpenCode.

```jsx
<AppSourceMark source="anthropic" />
<AppProviderMark source="anthropic" color="var(--anthropic)" /> Anthropic — Padrão
```

**Recognition before reading.** The card said whose it was through its title and the 2px stroke; the
HUD through the account name — both had to be *read*. Codenotch and ai-usagebar identify a provider
by its mark first, and this is that.

**Monochrome, tinted by the caller.** The source accent in the card header (the identity the stroke
already carries); the foreground in the middle of a HUD ring. A mark never brings its own brand
color — brand colors would not clear AA on both surfaces, and the accent is the identity already
measured by `AppAccentsContrastTest`.

**Decorative for semantics.** The provider name is always written next to it; a description here
would make assistive tech read the name twice.

**Sources and licences**, the same shapes ai-usagebar ships (MIT): Simple Icons `claude`, `openai`,
`cursor`, `deepseek`, `minimax`, `openrouter` and `googlegemini` (CC0-1.0); lobe-icons
`antigravity`, `kilocode`, `opencode` (MIT). All in a 24×24 viewBox. An exhaustive mapping over the
sources makes a new source without a mark a compile error, not an unidentified card.

This is the one exception to "no icon library": a mark is identification, not a control glyph, and
the control glyphs stay Unicode in Plex Mono.
