Dialog inside a window: a short question, the action it proposes and one that gives up. Every
in-window dialog of the app goes through it — API key, bug report, refresh warning, the team
removals and `AppConfirmationDialog`.

```jsx
<AppDialog
  title="Atualizar agora?"
  actions={<>
    <AppButton variant="ghost">Cancelar</AppButton>
    <AppButton variant="primary">Atualizar</AppButton>
  </>}
>
  Forçar uma atualização antes do horário agendado consome cota da API.
</AppDialog>
```

**It replaces Material's `AlertDialog`**, for the same reason `AppMenu` is not the `DropdownMenu`:
that one brings its own surface, radius and type, and above all it **appears in one frame** — on the
desktop it has no transition at all. The card popping in over a scrim that darkens at once was the
"abrupt" opening the modals were blamed for. The Compose API keeps the `AlertDialog` slots (`title`,
`text`, `confirmButton`, `dismissButton`) so every call site migrated mechanically.

**Entry: the scrim fades in (`--dur-select`) and the card enters with fade + scale 0.96 → 1** on the
GENTLE spring — no rebound, because the card carries text that must settle legible. Same starting
scale as `AppMenu`: the two surfaces are born the same way.

**The exit is dry, on purpose.** The caller removes the dialog in the same click that fires the
action; holding it composed to animate out would force every caller to keep content it already
discarded — and what leaves no longer matters (`--dur-exit`).

**Anatomy**: `surface` card, 1px `border`, radius `--r4` (10), `DIALOG` depth, `--s4` padding,
280–560 wide. Title mono, body sans muted, actions right-aligned with the dismiss one first.

**Scrim click and Esc ask to close; a click on the card never does.** The platform `Dialog` stays
underneath for the focus trap and Esc. The scrim listens to raw taps, not `clickable`: that one
merges its descendants' semantics and the whole card would collapse into one node for screen readers
and tests.

"Reduzir animações" turns the entry into an instant swap.
