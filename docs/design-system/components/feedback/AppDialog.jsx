import React from 'react';

// Diálogo dentro da janela. O fundo escurece por fade e o cartão entra com fade
// e escala de 0,96 a 1 (mola GENTLE, sem rebote). A saída é seca: quem fecha o
// diálogo é a ação que o usuário acabou de disparar.
const ENTER = `
@keyframes app-dialog-scrim { from { opacity: 0 } to { opacity: 1 } }
@keyframes app-dialog-card { from { opacity: 0; transform: scale(.96) } to { opacity: 1; transform: none } }
`;

export function AppDialog({ open = true, title, children, actions, onDismiss, style }) {
  if (!open) return null;
  return (
    <div
      onClick={onDismiss}
      style={{
        position: 'absolute',
        inset: 0,
        display: 'grid',
        placeItems: 'center',
        background: 'rgb(0 0 0 / .32)',
        animation: 'app-dialog-scrim var(--dur-select) var(--ease) both'
      }}
    >
      <style>{ENTER}</style>
      <div
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
        style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r4)',
          padding: 'var(--s4)',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--s3)',
          minWidth: 280,
          maxWidth: 560,
          boxShadow: 'var(--shadow-8)',
          animation: 'app-dialog-card var(--spring-gentle) both',
          ...style
        }}
      >
        {title ? (
          <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600 }}>{title}</span>
        ) : null}
        {children ? (
          <div style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>{children}</div>
        ) : null}
        <div style={{ display: 'flex', gap: 'var(--s2)', justifyContent: 'flex-end' }}>{actions}</div>
      </div>
    </div>
  );
}
