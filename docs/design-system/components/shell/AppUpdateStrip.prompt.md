The update lifecycle, one 28dp line, four states.

```jsx
<AppUpdateStrip state="available" message="Versão 36.0.0 disponível" action={<AppButton variant="ghost">Ver release</AppButton>} />
<AppUpdateStrip state="downloading" message="Baixando 36.0.0" progress={62} />
<AppUpdateStrip state="ready" message="36.0.0 pronta para instalar" action={<AppButton variant="ghost">Reiniciar e atualizar agora</AppButton>} />
<AppUpdateStrip state="failed" message="Falha ao baixar 36.0.0 — baixe manualmente" action={<AppButton variant="ghost">Abrir release</AppButton>} />
```

`failed` always offers the manual path — SmartScreen or antivirus blocking the unsigned Setup.exe is an expected outcome, not an exception.
