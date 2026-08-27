Dot + word, the app's only state badge. Never ship the dot without the word.

```jsx
<AppStatusIndicator level="warn" title="Cota Sessão 5h · No ritmo atual, a cota deve esgotar antes do reset. Previsão: Qua 13/08 13h00 BRT.">
  Atenção
</AppStatusIndicator>
<AppStatusIndicator level="off">Desconectado</AppStatusIndicator>
```

Rules baked into the wording: `Normal` = the quota should reset before it runs out. `Atenção`/`Crítico` = it should run out before the reset, with the forecast. Prepaid balance is judged by days of autonomy instead (<7d crit, <14d warn), and prints the date even when Normal.
