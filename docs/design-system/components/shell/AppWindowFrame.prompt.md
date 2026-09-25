Wraps any full screen in this system — dashboard, history, CLI sessions, team, settings.

```jsx
<AppWindowFrame title="Usage Monitor" dense footer={<AppStatusBar left={…} right={…} />}>
  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--s3)' }}>…</div>
</AppWindowFrame>
```

`dense` for the dashboard only; every other window opens wide and uses `--s4`.

**Dialog windows live on after the first open, and fade in only once painted** (`AppDialogWindow`).
History, CLI sessions, Codex sessions, team usage, presence, account keys, settings, help and release
notes all go through one host:

- **Closing hides, it does not destroy.** Recreating the window paid native peer + Skia context +
  full composition on every click — measured on Windows 11 with a warm JVM: 200–460 ms to the first
  frame, against 30–46 ms to show a hidden window again (13 ms measured through the real host).
  That wait was the "slow" modal. The open/close request reaches the window through a flow, never
  through recomposition: a hidden window does not recompose, and a first version that reacted to
  `visible` inside the window opened once and never reopened.
- **Entry waits for the first painted frame.** The frame's own scale used to start at composition,
  inside a window the OS showed at once and fully opaque; the first frames were lost to creation cost
  and what the eye saw was the window popping. Now the window appears **transparent**, the host waits
  two frames, then fades the AWT window's opacity 0 → 1 (`--dur-select`) while the content grows
  0.96 → 1 on the GENTLE spring. Window opacity, not content alpha: content fading inside an opaque
  window would show the window's own ground, not what is behind it.
- **Every close takes the same path**: the × button, Alt+F4, Esc and the content's own "Fechar" all
  just ask to close, and the drop of `visible` fades (140ms, scale back to 0.96) and hides. Before,
  only the × faded.
- Platforms without window translucency, and "Reduzir animações", open and close at once.

**The HUD is not a mode of this frame.** It has its own undecorated, always-on-top window
(`HudWindowHost`); the main window is hidden with its geometry intact while the HUD is shown.
