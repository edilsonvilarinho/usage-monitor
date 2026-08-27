import React from 'react';

const BASE = {
  fontFamily: 'var(--mono)',
  fontSize: 'var(--t12)',
  lineHeight: 1,
  padding: '7px 11px',
  height: 'var(--h-control)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--r2)',
  background: 'var(--surface)',
  color: 'var(--fg)',
  cursor: 'default',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '6px',
  whiteSpace: 'nowrap',
  transition: 'background var(--dur-hover) var(--ease), color var(--dur-hover) var(--ease)'
};

const VARIANTS = {
  primary: { background: 'var(--fg)', color: 'var(--bg)', borderColor: 'var(--fg)' },
  default: {},
  ghost:   { background: 'transparent', borderColor: 'transparent', color: 'var(--muted)' },
  danger:  { color: 'var(--crit)', borderColor: 'color-mix(in srgb, var(--crit) 45%, var(--border))' }
};

const HOVER = {
  primary: { background: 'color-mix(in srgb, var(--fg) 88%, var(--bg))' },
  default: { background: 'var(--raised)' },
  ghost:   { background: 'var(--raised)', color: 'var(--fg)' },
  danger:  { background: 'color-mix(in srgb, var(--crit) 12%, var(--surface))' }
};

export function AppButton({ variant = 'default', disabled = false, fullWidth = false, leading, children, onClick, title, style, ...rest }) {
  const [hover, setHover] = React.useState(false);
  const s = {
    ...BASE,
    ...(VARIANTS[variant] || {}),
    ...(hover && !disabled ? HOVER[variant] || {} : {}),
    ...(fullWidth ? { width: '100%' } : {}),
    ...(disabled ? { opacity: .42 } : {}),
    ...style
  };
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={disabled ? undefined : onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={s}
      {...rest}
    >
      {leading}
      {children}
    </button>
  );
}
