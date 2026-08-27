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
- No shadow, no gradient, no accent glow at the top. Depth is border + spacing.
