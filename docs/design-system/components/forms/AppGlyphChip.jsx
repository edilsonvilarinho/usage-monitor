import React from 'react';

export function AppGlyphChip({ description, selected = false, onClick, children, style }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={description}
      title={description}
      onClick={onClick}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 2,
        fontFamily: 'var(--mono)', fontSize: 'var(--t12)',
        background: selected ? 'var(--raised)' : 'var(--surface)',
        border: `1px solid ${selected ? 'var(--muted)' : 'var(--border)'}`,
        borderRadius: 'var(--r1)',
        color: selected ? 'var(--fg)' : 'var(--muted)',
        padding: '4px 4px', cursor: 'default', whiteSpace: 'nowrap',
        transition: 'background var(--dur-select) var(--ease)',
        ...style
      }}
    >
      <span style={{ fontSize: 14, lineHeight: 1 }}>{children}</span>
      <span style={{ width: 10, textAlign: 'center' }}>{selected ? '✓' : ''}</span>
    </button>
  );
}
