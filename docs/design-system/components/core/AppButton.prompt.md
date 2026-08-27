Text button for every committing, refreshing or navigating action — the app's only text-button primitive.

```jsx
<AppButton variant="primary" onClick={save}>Salvar</AppButton>
<AppButton onClick={refresh}>Atualizar</AppButton>
<AppButton variant="ghost" onClick={close}>Cancelar</AppButton>
<AppButton variant="danger" onClick={remove}>Remover</AppButton>
<AppButton disabled title="Indisponível no macOS: o DMG não tem Developer ID.">Atualizar agora</AppButton>
```

- One `primary` per surface, maximum. Panels and status bars use `ghost`.
- A disabled button ALWAYS carries `title` with the reason. A grey control with no explanation is worse than no control.
- No circular buttons, no pill radius, no uppercase labels.
