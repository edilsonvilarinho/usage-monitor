The window selector. Every screen that slices usage by time uses exactly these four labels.

```jsx
<AppSegmentedControl items={['5h', '7 dias', '30 dias', 'Total']} value={win} onChange={setWin} />
```

`5h` is anchored on the account's quota reset, not on the last five wall-clock hours. Say so in the surrounding copy when the distinction matters.

**Motion.** The selected fill is one thumb sliding behind the labels on the `SNAPPY` spring —
switching from "5h" to "30 dias" shows the path between the two instead of one segment going dark
and another lighting in the same frame. Labels crossfade in 180ms. The dividers stay on top of the
thumb.
