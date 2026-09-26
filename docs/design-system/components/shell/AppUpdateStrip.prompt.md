The update lifecycle, one line, four states. The action is a real `AppButton` in the `default`
variant (issue #291): a label with an arrow over a clickable strip did not read as a button, so the
strip itself is no longer clickable, and the line grows to the button's control height.

```jsx
<AppUpdateStrip state="available" message="Versão 36.0.0 disponível" action={<AppButton>Baixar atualização</AppButton>} />
<AppUpdateStrip state="downloading" message="Baixando 36.0.0" progress={62} />
<AppUpdateStrip state="ready" message="36.0.0 pronta para instalar" action={<AppButton>Reiniciar o app e atualizar</AppButton>} />
<AppUpdateStrip state="failed" message="Falha ao baixar 36.0.0 — baixe manualmente" action={<AppButton>Baixar manualmente</AppButton>} />
```

`failed` always offers the manual path — SmartScreen or antivirus blocking the unsigned Setup.exe is an expected outcome, not an exception.
