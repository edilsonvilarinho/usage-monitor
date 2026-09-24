Wraps any full screen in this system — dashboard, history, CLI sessions, team, settings.

```jsx
<AppWindowFrame title="Usage Monitor" dense footer={<AppStatusBar left={…} right={…} />}>
  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s3)' }}>…</div>
</AppWindowFrame>
```

`dense` for the dashboard only; every other window opens wide and uses `--s4`.

**Dialog windows enter and leave.** They already entered with scale 0.94 → 1 and a 14dp rise; they
now also leave: the close button fades the AWT window's opacity to zero in 140ms and only then asks
to close — the content fading inside an opaque window would show the window's own ground, not what
is behind it. Platforms without window translucency, and "Reduzir animações", close at once.

**The HUD is not a mode of this frame.** It has its own undecorated, always-on-top window
(`HudWindowHost`); the main window is hidden with its geometry intact while the HUD is shown.
