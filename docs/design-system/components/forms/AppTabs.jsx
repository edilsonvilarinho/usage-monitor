import React from 'react';

export function AppTabs({ items = [], value, onChange, style }) {
  return (
    <div role="tablist" style={{ display: 'flex', gap: 'var(--s3)', borderBottom: '1px solid var(--border)', ...style }}>
      {items.map((it) => {
        const id = typeof it === 'string' ? it : it.id;
        const label = typeof it === 'string' ? it : it.label;
        const on = id === value;
        return (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onChange && onChange(id)}
            style={{
              fontFamily: 'var(--mono)',
              fontSize: 'var(--t12)',
              background: 'none',
              border: 0,
              color: on ? 'var(--fg)' : 'var(--muted)',
              padding: '6px 2px',
              borderBottom: '2px solid ' + (on ? 'var(--fg)' : 'transparent'),
              cursor: 'default',
              transition: 'color var(--dur-select) var(--ease)'
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
