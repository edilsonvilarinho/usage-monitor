import React from 'react';

export function AppMetric({ label, value, hint, size = 'md', align = 'start', style }) {
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        padding: 'var(--s2) var(--s3)',
        background: 'var(--surface)',
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        alignItems: align === 'center' ? 'center' : 'flex-start',
        minWidth: 0,
        ...style
      }}
    >
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: 'var(--ls-eyebrow)', textTransform: 'uppercase', color: 'var(--muted)', whiteSpace: 'nowrap' }}>{label}</span>
      <span
        style={{
          fontFamily: 'var(--mono)',
          fontVariantNumeric: 'tabular-nums',
          fontSize: size === 'lg' ? 'var(--t20)' : size === 'sm' ? 'var(--t12)' : 'var(--t16)',
          fontWeight: size === 'lg' ? 500 : 400,
          letterSpacing: size === 'lg' ? 'var(--ls-title)' : 'normal'
        }}
      >
        {value}
      </span>
      {hint ? <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', color: 'var(--muted)' }}>{hint}</span> : null}
    </div>
  );
}
