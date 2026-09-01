Confirmation required before a destructive action. Nothing in this app deletes on a single click.

```jsx
<AppConfirmationDialog
  title="Remover esta conta do time?"
  message="Apaga tudo o que pessoal@gmail.com enviou — integrantes, sessões e turnos — e passa a recusá-la, mesmo que a máquina dela continue enviando. Os dados não voltam nem depois de devolvê-la ao time."
  confirmLabel="Remover do time"
  cancelLabel="Cancelar"
/>
```

The confirming button is ALWAYS `danger` and the cancelling one is `ghost`. Someone reading this
dialog is about to delete something, and the button that undoes cannot carry the same weight as the
one that executes.

The message states the consequence, not the action — the title already named the action. Say what
goes away and what does not come back.

The body scrolls above 280px so the buttons never leave the viewport: a confirmation whose confirm
button is off-screen is a dialog that cannot be answered.
