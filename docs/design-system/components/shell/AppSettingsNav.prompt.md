The Settings dialog's left rail. Sections: Geral, Alertas, APIs, Contas, Time.

```jsx
<div style={{ display: 'flex', minHeight: 320 }}>
  <AppSettingsNav items={['Geral','Alertas','APIs','Contas','Time']} value={sec} onChange={setSec} />
  <div style={{ flex: 1, padding: 'var(--s4)' }}>{/* only the selected section */}</div>
</div>
```

Mount only the selected section.
