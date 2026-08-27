import React from 'react';

export function AppSegmentedControl({ items = [], value, onChange, style }) {
  return (
    <div style={{ display: 'inline-flex', border: '1px solid var(--border)', borderRadius: 'var(--r2)', overflow: 'hidden', ...style }}>
      {items.map((it, i) => {
        const id = typeof it === 'string' ? it : it.id;
        const label = typeof it === 'string' ? it : it.label;
        const on = id === value;
        return (
          <button
            key={id}
            type="button"
            aria-pressed={on}
            onClick={() => onChange && onChange(id)}
            style={{
              fontFamily: 'var(--mono)',
              fontSize: 'var(--t12)',
              background: on ? 'var(--raised)' : 'var(--surface)',
              border: 0,
              borderRight: i === items.length - 1 ? 0 : '1px solid var(--border)',
              color: on ? 'var(--fg)' : 'var(--muted)',
              padding: '6px 10px',
              cursor: 'default',
              whiteSpace: 'nowrap',
              transition: 'background var(--dur-select) var(--ease)'
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
