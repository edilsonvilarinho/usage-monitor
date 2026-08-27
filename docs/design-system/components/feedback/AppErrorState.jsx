import React from 'react';

export function AppErrorState({ message, detail, action, style }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--s2)', padding: '44px var(--s4)', textAlign: 'center', ...style }}>
      <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--crit)' }} />
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', maxWidth: '46ch' }}>{message}</span>
      {detail ? <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxWidth: '52ch' }}>{detail}</span> : null}
      {action}
    </div>
  );
}
