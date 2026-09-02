import React from 'react';
import { AppStatusIndicator } from '../data/AppStatusIndicator.jsx';
import { AppStatusDot } from '../data/AppStatusDot.jsx';

export function AppHudBar({
  level = 'ok',
  label,
  sourceLabel,
  resetLabel,
  dotOnly = false,
  expanded = false,
  sources = [],
  onOpen,
  style
}) {
  const showPanel = expanded && sources.length > 0;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        maxWidth: 320,
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        boxShadow: 'var(--shadow-8)',
        background: 'var(--surface)',
        overflow: 'hidden',
        ...style
      }}
    >
      <div
        role="button"
        tabIndex={0}
        aria-label="Abrir Usage Monitor"
        onClick={onOpen}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--s3)',
          height: 'var(--h-hud)',
          // A pílula recolhida ao ponto não pode carregar o padding da pílula
          // com texto: um ponto de 6dp viraria uma janela de 38dp.
          padding: dotOnly ? '0 var(--s2)' : '0 var(--s3)',
          cursor: 'default',
          flex: 'none',
          minWidth: 0
        }}
      >
        {dotOnly ? (
          <AppStatusDot level={level} />
        ) : (
          <>
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
          </>
        )}
      </div>

      {showPanel ? (
        <>
          <div style={{ height: 1, background: 'var(--border)' }} />
          <div style={{ padding: 'var(--s1) var(--s3)' }}>
            {sources.map((source) => (
              <div
                key={source.label}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--s3)',
                  // 20dp: a linha do painel não é AppDataRow, que floora em
                  // 32dp mais padding — seis fontes dariam ~288dp de painel.
                  height: 20
                }}
              >
                <span style={{ flex: 1, minWidth: 0, fontFamily: 'var(--mono)', fontSize: 'var(--t12)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {source.label}
                </span>
                <AppStatusIndicator level={source.level}>{source.statusLabel}</AppStatusIndicator>
              </div>
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}
