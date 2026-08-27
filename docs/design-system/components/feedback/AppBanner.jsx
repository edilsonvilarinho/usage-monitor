import React from 'react';

const LEVELS = { info: 'var(--info)', warn: 'var(--warn)', crit: 'var(--crit)', neutral: 'var(--muted)' };

export function AppBanner({ level = 'info', title, children, action, style }) {
  return (
    <div
      role={level === 'crit' ? 'alert' : 'status'}
      style={{
        display: 'flex',
        gap: 'var(--s3)',
        alignItems: 'flex-start',
        padding: 'var(--s2) var(--s3)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        background: 'var(--surface)',
        minWidth: 0,
        ...style
      }}
    >
      <span aria-hidden="true" style={{ width: 'var(--mark-w)', alignSelf: 'stretch', borderRadius: 1, flex: 'none', background: LEVELS[level] || LEVELS.neutral }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600 }}>{title}</span>
        {children ? <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>{children}</span> : null}
      </div>
      <span style={{ flex: 1 }} />
      {action}
    </div>
  );
}
