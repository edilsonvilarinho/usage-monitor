import React from 'react';

const WIDTHS = ['38%', '82%', '64%', '74%'];

export function AppLoadingState({ lines = 4, message = 'Carregando dados das APIs…', style }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--s2)', padding: 'var(--s4)', ...style }}>
      {Array.from({ length: lines }).map((_, i) => (
        <span key={i} style={{ height: 10, width: WIDTHS[i % WIDTHS.length], borderRadius: 'var(--r1)', background: 'var(--raised)', border: '1px solid var(--border)' }} />
      ))}
      {message ? (
        <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: 'var(--ls-eyebrow)', textTransform: 'uppercase', color: 'var(--muted)', marginTop: 'var(--s1)' }}>{message}</span>
      ) : null}
    </div>
  );
}
