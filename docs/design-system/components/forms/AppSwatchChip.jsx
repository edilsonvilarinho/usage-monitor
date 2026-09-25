import React from 'react';

export function AppSwatchChip({ label, swatch = null, selected = false, onClick, style }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        fontFamily: 'var(--mono)', fontSize: 'var(--t12)',
        background: selected ? 'var(--raised)' : 'var(--surface)',
        border: `1px solid ${selected ? 'var(--muted)' : 'var(--border)'}`,
        borderRadius: 'var(--r1)',
        color: selected ? 'var(--fg)' : 'var(--muted)',
        padding: '6px 8px', cursor: 'default', whiteSpace: 'nowrap',
        transition: 'background var(--dur-select) var(--ease)',
        ...style
      }}
    >
      <span style={{
        width: 10, height: 10, borderRadius: '50%', flex: 'none',
        background: swatch || 'transparent',
        border: swatch ? 0 : '1px solid var(--border-strong, var(--muted))'
      }} />
      {label}
      <span style={{ width: 10, textAlign: 'center' }}>{selected ? '✓' : ''}</span>
    </button>
  );
}
