Persistent notice. The app has no toasts: a monitor that refreshes every 10 minutes must not report failures with something that disappears.

```jsx
<AppBanner level="warn" title="Anthropic — Padrão" action={<AppButton variant="ghost">Tentar de novo</AppButton>}>
  Limite de requisições atingido. Aguarde antes de tentar de novo.
</AppBanner>
<AppBanner level="info" title="Versão 36.0.0 disponível" action={<AppButton variant="ghost">Ver release</AppButton>} />
```

A banner stays visible on a MINIMIZED card. It was a closed card that hid the August credits incident.

**The update strip slides.** It enters and leaves through `AppExpandable` (grows from the top on the
`GENTLE` spring, recedes in a short tween), drawing its last state while it leaves. Transient notices
(snackbar) overlay the bottom of the window instead of pushing the content.
