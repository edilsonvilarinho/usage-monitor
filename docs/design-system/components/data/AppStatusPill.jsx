import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// Fundo em 8% do tom: o maior valor em que o texto no próprio tom ainda passa
// AA nos dois temas (o verde do tema claro cai para 4,17:1 com 14%).
export function AppStatusPill({ level = 'ok', children, title, style }) {
  const color = LEVELS[level] || LEVELS.ok;
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '2px 6px',
        borderRadius: 'var(--r1)',
        background: `color-mix(in srgb, ${color} 8%, transparent)`,
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t10)',
        letterSpacing: '.06em',
        whiteSpace: 'nowrap',
        color,
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
