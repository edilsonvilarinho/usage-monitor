import React from 'react';

export function AppStatusBar({ left, right, position = 'bottom', style, children }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s3)',
        height: 'var(--h-statusbar)',
        padding: '0 var(--s3)',
        borderTop: position === 'bottom' ? '1px solid var(--border)' : 'none',
        borderBottom: position === 'top' ? '1px solid var(--border)' : 'none',
        background: 'var(--surface)',
        fontFamily: 'var(--mono)',
        fontSize: 'var(--t10)',
        color: 'var(--muted)',
        flex: 'none',
        minWidth: 0,
        ...style
      }}
    >
      {children != null ? children : (
        <React.Fragment>
          {left}
          <span style={{ flex: 1 }} />
          {right}
        </React.Fragment>
      )}
    </div>
  );
}
