The whole surface failed. Reach for `AppBanner` when only one source failed — partial success is a first-class UiState here.

```jsx
<AppErrorState message="Erro ao carregar histórico" action={<AppButton>Tentar novamente</AppButton>} />
```

Children go between the message and the detail — the team windows put an `AppStepTrail` there (issue #494). A second,
ghost action may sit beside the retry ("Copy technical detail"); with no action at all, no button row is drawn.
