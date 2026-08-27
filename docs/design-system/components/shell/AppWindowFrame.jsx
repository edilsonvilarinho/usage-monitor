import React from 'react';

const MARK = (
  <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true" style={{ flex: 'none' }}>
    <rect width="24" height="24" rx="5" fill="var(--fg)" />
    <path d="M7 6.5v7.2a2.6 2.6 0 0 0 2.6 2.6h4.8a2.6 2.6 0 0 0 2.6-2.6V6.5" stroke="var(--bg)" strokeWidth="2.4" fill="none" strokeLinecap="round" />
    <path d="M12 6.5v6.6" stroke="var(--bg)" strokeWidth="2.4" strokeLinecap="round" />
  </svg>
);

function WinBtn({ glyph, label, close = false }) {
  const [hover, setHover] = React.useState(false);
  return (
    <span
      role="button"
      aria-label={label}
      title={label}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        width: 40,
        height: 33,
        display: 'grid',
        placeItems: 'center',
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t12)',
        cursor: 'default',
        color: hover ? (close ? '#fff' : 'var(--fg)') : 'var(--muted)',
        background: hover ? (close ? 'var(--crit)' : 'var(--raised)') : 'transparent',
        transition: 'background var(--dur-hover) var(--ease)'
      }}
    >
      {glyph}
    </span>
  );
}

export function AppWindowFrame({ title, chrome = true, showMinimize = true, showMaximize = true, width, dense = false, children, footer, style }) {
  return (
    <div
      style={{
        background: 'var(--bg)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r4)',
        boxShadow: 'var(--shadow-8)',
        overflow: 'hidden',
        fontFamily: 'var(--sans)',
        color: 'var(--fg)',
        width: width,
        maxWidth: '100%',
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0,
        ...style
      }}
    >
      {chrome ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', height: 'var(--h-titlebar)', padding: '0 0 0 var(--s3)', background: 'var(--surface)', borderBottom: '1px solid var(--border)', flex: 'none' }}>
          {MARK}
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 500, flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</span>
          {showMinimize ? <WinBtn glyph="–" label="Minimizar janela" /> : null}
          {showMaximize ? <WinBtn glyph="□" label="Maximizar janela" /> : null}
          <WinBtn glyph="×" label="Fechar janela" close />
        </div>
      ) : null}
      <div style={{ padding: dense ? 'var(--s3)' : 'var(--s4)', display: 'flex', flexDirection: 'column', gap: dense ? 'var(--s2)' : 'var(--s3)', minWidth: 0, flex: 1 }}>
        {children}
      </div>
      {footer}
    </div>
  );
}
