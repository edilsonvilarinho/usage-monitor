import React from 'react';

export function AppSettingsNav({ items = [], value, onChange, style }) {
  return (
    <nav
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        width: 168,
        flex: 'none',
        padding: 'var(--s2)',
        borderRight: '1px solid var(--border)',
        background: 'var(--surface)',
        ...style
      }}
    >
      {items.map((it) => {
        const id = typeof it === 'string' ? it : it.id;
        const label = typeof it === 'string' ? it : it.label;
        const on = id === value;
        return (
          <NavItem key={id} label={label} on={on} onClick={() => onChange && onChange(id)} />
        );
      })}
    </nav>
  );
}

function NavItem({ label, on, onClick }) {
  const [hover, setHover] = React.useState(false);
  return (
    <button
      type="button"
      aria-current={on || undefined}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        textAlign: 'left',
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t12)',
        padding: '6px var(--s2)',
        border: 0,
        borderLeft: '2px solid ' + (on ? 'var(--fg)' : 'transparent'),
        borderRadius: 'var(--r1)',
        background: on || hover ? 'var(--raised)' : 'transparent',
        color: on ? 'var(--fg)' : 'var(--muted)',
        cursor: 'default',
        transition: 'background var(--dur-hover) var(--ease)'
      }}
    >
      {label}
    </button>
  );
}
