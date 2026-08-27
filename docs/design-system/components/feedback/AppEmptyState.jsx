import React from 'react';

export function AppEmptyState({ glyph = '·', message, action, style }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--s2)', padding: '44px var(--s4)', textAlign: 'center', ...style }}>
      <span aria-hidden="true" style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t20)', color: 'var(--muted)' }}>{glyph}</span>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '46ch' }}>{message}</span>
      {action}
    </div>
  );
}
