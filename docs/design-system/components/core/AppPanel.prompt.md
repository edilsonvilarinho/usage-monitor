The data surface. Wrap every group of data on every screen; never invent another rectangle.

```jsx
<AppPanel>
  <AppPanelHeader
    mark={<AppSourceMark source="anthropic" />}
    title="Anthropic · Padrão"
    subtitle="dev@example.com — Example Org"
    status={<AppStatusIndicator level="warn">Atenção</AppStatusIndicator>}
    actions={<AppIconButton glyph="↻" label="Atualizar" />}
  />
  <AppPanelBody flush>
    <AppDataRow>…</AppDataRow>
  </AppPanelBody>
</AppPanel>
```

- `flush` when the body is a list of `AppDataRow` (the rows carry the dividers).
- Depth `CARD`: `--shadow-card`, a 1dp `--highlight` inside the top, the neutral `--sheen` over the
  first 56px, and a border lit on top (`--border-top` → `--border-bottom`). In dark the shadow is
  nearly invisible and the light does the work.
- Never an accent glow or accent gradient. Blocks *inside* a panel are `FLAT`: a shadow inside a
  surface is the stack of same-weight blocks the August refactor removed.

**Card header identity.** In the dashboard card the header reads: 2dp stroke · provider mark in the
source accent (`AppProviderMark`) · title ("Anthropic — Padrão") with the **account plan** as a small
bordered badge right after it ("Max 20x", "ChatGPT Plus") · email below. The plan badge sits on the
title line, not after the column — the column is as wide as the email, and the badge would drift
away from the name it qualifies. No plan known, no badge.
