import React from 'react';

export function AppGroupBand({ label, detail, indent = 0, horizontalPadding = 12, trailing, style }) {
  return (
    <div style={{ width: '100%', ...style }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          background: 'var(--surface)',
          paddingLeft: horizontalPadding + indent,
          paddingRight: horizontalPadding,
          paddingTop: 'var(--s2)',
          paddingBottom: 'var(--s2)'
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontFamily: 'var(--mono)',
              fontSize: 'var(--t10)',
              color: 'var(--muted)',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis'
            }}
          >
            {label}
          </div>
          {detail && (
            <div
              style={{
                fontFamily: 'var(--mono)',
                fontSize: 'var(--t10)',
                color: 'var(--muted)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis'
              }}
            >
              {detail}
            </div>
          )}
        </div>
        {trailing && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s1)' }}>{trailing}</div>
        )}
      </div>
      <div style={{ height: 1, background: 'var(--border)' }} />
    </div>
  );
}
