import React from 'react';

export function AppToolbar({ children, style }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s3)',
        height: 'var(--h-toolbar)',
        padding: '0 var(--s3)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        background: 'var(--surface)',
        flex: 'none',
        minWidth: 0,
        overflowX: 'auto',
        ...style
      }}
    >
      {children}
    </div>
  );
}
