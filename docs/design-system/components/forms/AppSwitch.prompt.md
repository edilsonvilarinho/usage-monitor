Boolean setting toggle. Auto-start, always-on-top, automatic update, team integration.

```jsx
<AppSwitch checked={autoStart} onChange={setAutoStart} label="Iniciar com o sistema" />
<AppSwitch checked={false} disabled label="Atualização automática"
  reason="macOS: o DMG não tem Developer ID, e remontar o bundle sob quarentena do Gatekeeper não fecha de forma confiável." />
```

A disabled switch without `reason` is a bug. The user must know whether it is their machine, their install source, or the build.
