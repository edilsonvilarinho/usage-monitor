Single-line input — session/project filters, team server URL, nickname, keys.

```jsx
<AppTextField placeholder="Filtrar projeto, branch ou modelo" value={q} onChange={setQ} />
<AppTextField label="Servidor do time" placeholder="https://usage.example.com" hint="A chave fica em ~/.usage-monitor/team.json, fora das preferências." />
```

Never put an explanation in the placeholder — placeholders disappear. Use `hint`.
