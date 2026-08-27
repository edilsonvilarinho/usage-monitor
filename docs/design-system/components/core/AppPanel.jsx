import React from 'react';

export function AppPanel({ children, style, ...rest }) {
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r3)',
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0,
        ...style
      }}
      {...rest}
    >
      {children}
    </div>
  );
}

export function AppPanelHeader({ title, subtitle, mark, status, actions, style }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s2)',
        padding: 'var(--s2) var(--s3)',
        borderBottom: '1px solid var(--border)',
        minWidth: 0,
        ...style
      }}
    >
      {mark}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 0, minWidth: 0 }}>
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600, letterSpacing: '.02em', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</span>
        {subtitle ? (
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{subtitle}</span>
        ) : null}
      </div>
      <span style={{ flex: 1 }} />
      {status}
      {actions}
    </div>
  );
}

export function AppPanelBody({ children, flush = false, dense = false, style }) {
  return (
    <div
      style={{
        padding: flush ? 0 : (dense ? 'var(--s2) var(--s3)' : 'var(--s3)'),
        display: 'flex',
        flexDirection: 'column',
        gap: flush ? 0 : 'var(--s2)',
        minWidth: 0,
        ...style
      }}
    >
      {children}
    </div>
  );
}
