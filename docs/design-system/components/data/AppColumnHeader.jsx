import React from 'react';

export function AppColumnHeader({ items = [], offset = 14, style }) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 'var(--s2)',
        padding: '0 var(--s3) var(--s2)',
        paddingLeft: 'calc(var(--s3) + ' + offset + 'px)',
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t10)',
        letterSpacing: 'var(--ls-eyebrow)',
        textTransform: 'uppercase',
        color: 'var(--muted)',
        whiteSpace: 'nowrap',
        ...style
      }}
    >
      {items.map((it, i) =>
        typeof it === 'string'
          ? <span key={i}>{it}</span>
          : <span key={i} style={{ flex: it.flex ? it.flex : 'none', textAlign: it.align, width: it.flex ? undefined : it.width, minWidth: it.width, overflow: 'hidden' }}>{it.label}</span>
      )}
    </div>
  );
}

/**
 * Legenda ordenável (#537). `sortDescending`: null = inativa, true = descendente, false = ascendente.
 * A seta só aparece na coluna ativa. Espelha `AppSortableColumnHeaderLabel` do Compose.
 */
export function AppSortableColumnHeaderLabel({ label, sortDescending = null, onClick, style }) {
  const active = sortDescending !== null;
  const arrow = sortDescending === false ? '↑' : '↓';
  const stateText = sortDescending === true ? 'maior primeiro' : sortDescending === false ? 'menor primeiro' : undefined;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-description={stateText}
      style={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        background: 'none',
        border: 'none',
        padding: 0,
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t10)',
        color: active ? 'var(--ink)' : 'var(--muted)',
        whiteSpace: 'nowrap',
        cursor: 'pointer',
        ...style
      }}
    >
      <span>{label}</span>
      {active && (
        <span aria-hidden="true" style={{ position: 'absolute', left: '100%', marginLeft: 'var(--s1)' }}>{arrow}</span>
      )}
    </button>
  );
}
