The window footer and the card action strip.

```jsx
<AppStatusBar
  left={<React.Fragment><span>v35.0.0</span><span>Próxima coleta em 06:41</span></React.Fragment>}
  right={<AppButton variant="ghost">Atualizar tudo</AppButton>}
/>
```

Never put an aggregated health claim here ("4 fontes" in green): with one source in error the green would lie. Source failure is the banner's job.
