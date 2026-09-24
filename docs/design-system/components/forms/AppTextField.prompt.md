Single-line input — session/project filters, team server URL, nickname, keys.

```jsx
<AppTextField placeholder="Filtrar projeto, branch ou modelo" value={q} onChange={setQ} />
<AppTextField label="Servidor do time" placeholder="https://usage.example.com" hint="A chave fica em ~/.usage-monitor/team.json, fora das preferências." />
```

Never put an explanation in the placeholder — placeholders disappear. Use `hint`.

**Focus.** A 2dp `--info` ring drawn inside the field, crossfading from the neutral 1dp stroke in
120ms. The readme always required it; the Compose field never had it, and in a five-field form
(Settings → Network) nothing said which one was receiving the typing.
