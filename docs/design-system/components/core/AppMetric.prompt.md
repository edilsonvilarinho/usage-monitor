Boxed single number — burn rate, window totals, cache savings, the quota badges on a minimized card.

```jsx
<AppMetric label="Ritmo" value="US$ 1,84/h" hint="projeção US$ 9,20 no fechamento" />
<AppMetric label="Sessão 5h" value="68%" size="lg" align="center" />
```

Group them in a grid with `gap: var(--s3)`. Never more than four across.

**Motion.** The value is an `AppAnimatedNumber`: when it changes, the new value enters from below if
it rose and from above if it fell, on the `GENTLE` spring with a crossfade. The direction says which
way the number moved before it is read. It formats nothing — the caller's text is swapped as is.
