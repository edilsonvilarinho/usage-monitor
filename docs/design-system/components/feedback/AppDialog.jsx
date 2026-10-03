import React from 'react';
import { AppModalRevealRow } from '../shell/AppModalRevealRow.jsx';

// Diálogo dentro da janela. O fundo escurece por fade e o cartão toca o E9
// (filamentos de plasma): aparece em ~100 ms e título, texto e ações são
// revelados em cascata atrás do filamento. Sem escala. A saída é seca: quem
// fecha o diálogo é a ação que o usuário acabou de disparar.
const ENTER = `
@keyframes app-dialog-scrim { from { opacity: 0 } to { opacity: 1 } }
@keyframes app-dialog-card { from { opacity: 0 } to { opacity: 1 } }
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
          animation: 'app-dialog-card 100ms var(--ease) both',
          ...style
        }}
      >
        {title ? (
          <AppModalRevealRow index={0} count={3}>
            <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600 }}>{title}</span>
          </AppModalRevealRow>
        ) : null}
        {children ? (
          <AppModalRevealRow index={1} count={3}>
            <div style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)' }}>{children}</div>
          </AppModalRevealRow>
        ) : null}
        <AppModalRevealRow index={2} count={3}>
          <div style={{ display: 'flex', gap: 'var(--s2)', justifyContent: 'flex-end' }}>{actions}</div>
        </AppModalRevealRow>
      </div>
    </div>
  );
}
