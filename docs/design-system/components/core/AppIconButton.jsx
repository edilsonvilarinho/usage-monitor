import React from 'react';

export function AppIconButton({ glyph, label, onClick, variant = 'default', size = 26, disabled = false, style, ...rest }) {
  const [hover, setHover] = React.useState(false);
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={disabled ? undefined : onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        width: size,
        height: size,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flex: 'none',
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t12)',
        lineHeight: 1,
        borderRadius: 'var(--r2)',
        border: variant === 'ghost' ? '1px solid transparent' : '1px solid var(--border)',
        background: hover && !disabled ? 'var(--raised)' : (variant === 'ghost' ? 'transparent' : 'var(--surface)'),
        color: hover && !disabled ? 'var(--fg)' : 'var(--muted)',
        cursor: 'default',
        opacity: disabled ? .42 : 1,
        transition: 'background var(--dur-hover) var(--ease), color var(--dur-hover) var(--ease)',
        ...style
      }}
      {...rest}
    >
      {glyph}
    </button>
  );
}
