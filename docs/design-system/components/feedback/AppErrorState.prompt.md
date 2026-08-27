The whole surface failed. Reach for `AppBanner` when only one source failed — partial success is a first-class UiState here.

```jsx
<AppErrorState message="Erro ao carregar histórico" action={<AppButton>Tentar novamente</AppButton>} />
```
