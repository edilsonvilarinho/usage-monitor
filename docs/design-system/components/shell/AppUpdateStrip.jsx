import React from 'react';

const LEVELS = { available: 'var(--info)', downloading: 'var(--info)', ready: 'var(--ok)', failed: 'var(--crit)' };

export function AppUpdateStrip({ state = 'available', message, progress, action, style }) {
  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s3)',
        height: 'var(--h-updatestrip)',
        padding: '0 var(--s3)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        background: 'var(--surface)',
        flex: 'none',
        minWidth: 0,
        ...style
      }}
    >
      <span aria-hidden="true" style={{ width: 'var(--mark-w)', height: 16, borderRadius: 1, flex: 'none', background: LEVELS[state] || LEVELS.available }} />
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{message}</span>
      {typeof progress === 'number' ? (
        <span style={{ flex: 1, minWidth: 60, maxWidth: 180, height: 'var(--track-h)', borderRadius: 2, background: 'var(--raised)', border: '1px solid var(--border)', overflow: 'hidden' }}>
          <span style={{ display: 'block', height: '100%', width: Math.max(0, Math.min(100, progress)) + '%', background: 'var(--info)' }} />
        </span>
      ) : <span style={{ flex: 1 }} />}
      {action}
    </div>
  );
}
