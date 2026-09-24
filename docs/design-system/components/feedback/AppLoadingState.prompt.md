First collection in flight. Static skeleton bars plus one mono-10 line saying what is loading.

```jsx
<AppLoadingState lines={4} message="Carregando dados das APIs…" />
```

Do not add a shimmer. Do not add a spinner.

**Leaving it.** Screens swap loading → data → error through `AppStateCrossfade`: the new state fades
in and rises 1/24 of its height, the old one fades out in 120ms. The content receives the state
**only through its parameter** — the dashboard and the history once read the current state from
outside, and both slots of the transition drew the new one.
