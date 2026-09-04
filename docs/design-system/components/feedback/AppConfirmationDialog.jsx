import React from 'react';
import { AppButton } from '../core/AppButton.jsx';

export function AppConfirmationDialog({ title, message, confirmLabel, cancelLabel, onConfirm, onDismiss, style }) {
  return (
    <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--r4)', padding: 'var(--s4)', display: 'flex', flexDirection: 'column', gap: 'var(--s3)', maxWidth: 520, boxShadow: 'var(--shadow-8)', ...style }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: 'var(--t12)', fontWeight: 600 }}>{title}</span>
      <span style={{ fontFamily: 'var(--sans)', fontSize: 'var(--t12)', color: 'var(--muted)', maxHeight: 280, overflowY: 'auto' }}>{message}</span>
      <div style={{ display: 'flex', gap: 'var(--s2)', justifyContent: 'flex-end' }}>
        <AppButton variant="ghost" onClick={onDismiss}>{cancelLabel}</AppButton>
        <AppButton variant="danger" onClick={onConfirm}>{confirmLabel}</AppButton>
      </div>
    </div>
  );
}
