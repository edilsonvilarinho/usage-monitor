Multi-line input — the only place a person writes prose: the bug report description.

```jsx
<AppTextArea label="O que aconteceu" placeholder="Descreva o que você fez e o que aconteceu." value={text} onChange={setText} />
```

Sibling of `AppTextField`, not a flag on it. That one is a filter, a URL, a nickname
or a key on one control-height line; this one grows downward and starts at the top.
A configurable `singleLine` would make one primitive answer two questions and leave
the minimum height without an owner.

Text is sans 12, not mono: this is prose, and mono exists here for labels, numbers
and column headers.
