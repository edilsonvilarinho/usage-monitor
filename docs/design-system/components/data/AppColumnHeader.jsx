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
