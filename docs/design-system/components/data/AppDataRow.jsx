import React from 'react';

export function AppDataRow({ children, mark, onClick, indent = 0, guide = false, last = false, hoverable = true, style }) {
  const [hover, setHover] = React.useState(false);
  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s3)',
        padding: 'var(--s2) var(--s3)',
        paddingLeft: indent ? 'calc(var(--s3) + ' + indent + 'px)' : 'var(--s3)',
        marginLeft: guide ? 24 : 0,
        borderLeft: guide ? 'var(--mark-w) solid var(--border)' : 'none',
        background: guide
          ? (hoverable && hover ? 'var(--raised)' : 'var(--surface)')
          : (hoverable && hover ? 'var(--raised)' : 'transparent'),
        borderBottom: last ? 'none' : '1px solid var(--border)',
        minWidth: 0,
        transition: 'background var(--dur-hover) var(--ease)',
        ...style
      }}
    >
      {mark}
      {children}
    </div>
  );
}

export function AppKey({ children, dim = false, style }) {
  return (
    <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t10)', letterSpacing: 'var(--ls-eyebrow)', textTransform: 'uppercase', color: 'var(--muted)', opacity: dim ? .85 : 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', display: 'block', ...style }}>{children}</span>
  );
}

export function AppValue({ children, size = 'md', dim = false, style }) {
  return (
    <span
      style={{
        fontFamily: 'var(--mono)',
        fontVariantNumeric: 'tabular-nums',
        fontSize: size === 'lg' ? 'var(--t20)' : size === 'sm' ? 'var(--t12)' : 'var(--t14)',
        fontWeight: size === 'lg' ? 500 : 400,
        letterSpacing: size === 'lg' ? 'var(--ls-title)' : 'normal',
        color: dim ? 'var(--muted)' : 'inherit',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        ...style
      }}
    >
      {children}
    </span>
  );
}
