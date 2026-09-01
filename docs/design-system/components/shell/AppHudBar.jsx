import React from 'react';
import { AppStatusIndicator } from '../data/AppStatusIndicator.jsx';

export function AppHudBar({ level = 'ok', label, sourceLabel, resetLabel, tooltipTitle, onOpen, style }) {
  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Abrir Usage Monitor"
      title={tooltipTitle}
      onClick={onOpen}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--s3)',
        height: 'var(--h-hud)',
        padding: '0 var(--s3)',
        border: '1px solid var(--border)',
        borderTopRightRadius: 0,
        borderBottomLeftRadius: 'var(--r2)',
        boxShadow: 'var(--shadow-8)',
        background: 'var(--surface)',
        cursor: 'default',
        flex: 'none',
        minWidth: 0,
        ...style
      }}
    >
      <AppStatusIndicator level={level}>{label}</AppStatusIndicator>
      {sourceLabel ? (
        <span style={{ flex: 1, minWidth: 0, fontFamily: 'var(--mono)', fontSize: 'var(--t12)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {sourceLabel}
        </span>
      ) : (
        <span style={{ flex: 1 }} />
      )}
      {resetLabel ? (
        <span style={{ flex: 'none', fontFamily: 'var(--mono)', fontSize: 'var(--t12)', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
          {resetLabel}
        </span>
      ) : null}
    </div>
  );
}
