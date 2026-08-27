import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

export function AppStatusIndicator({ level = 'ok', children, title, style }) {
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t10)',
        letterSpacing: '.06em',
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
        color: LEVELS[level] || LEVELS.ok,
        ...style
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          flex: 'none',
          background: level === 'off' ? 'transparent' : 'currentColor',
          border: level === 'off' ? '1px solid currentColor' : 'none'
        }}
      />
      {children}
    </span>
  );
}
