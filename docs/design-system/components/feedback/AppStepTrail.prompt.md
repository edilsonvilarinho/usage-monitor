Where a multi-step operation stopped (issue #494). Today it shows the connection to the team server: Address ›
Connection › Certificate › Server › Key (or Admin token). Two forms of the same piece:

- `AppStepTrail`, compact: one wrapping line, inside `AppErrorState` (its `content` slot) in the team windows.
- `AppStepList`, full: one step per line with what happened there, in Settings › Team ("Test connection", "Validate").

Rules:

- Every state has its **written glyph** — `✓` done, `×` failed, `–` not reached, `?` not measured. Color only
  reinforces: the failed step is critical, not reached and not measured are muted.
- A step is `done` only when the failure **after** it proves it passed (a TLS failure proves the name resolved and
  the port answered). What the failure does not prove is `unknown`, never `done`.
- Steps after the failure are `notReached`: the user sees that the key was never tested.
- The headline says the stage and the reason ("Stopped at Certificate — expired Oct 27, 12:21 BRT"); the raw
  exception text goes to "Copy technical detail", never to the title.
- Static: no animation; it is a state, not a progress bar.

```jsx
<AppErrorState message="Stopped at Certificate — expired Oct 27, 12:21 BRT"
  detail="The server's certificate expired: whoever runs the server has to renew it. Your key was not tested."
  action={<AppButton>Try again</AppButton>}>
  <AppStepTrail steps={[
    { label: 'Address', status: 'done' }, { label: 'Connection', status: 'done' },
    { label: 'Certificate', status: 'failed' }, { label: 'Server', status: 'notReached' },
    { label: 'Key', status: 'notReached' },
  ]} />
</AppErrorState>
```

Compose: `AppStepTrail`/`AppStepList` in `presentation/ui/components/AppStepTrail.kt`; the team wrappers
(`TeamConnectionErrorState`, `TeamConnectionNotice`, `TeamConnectionPath`) in `presentation/ui/TeamConnectionFailureViews.kt`.
