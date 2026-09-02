import React from 'react';
import { AppStatusIndicator } from '../data/AppStatusIndicator.jsx';
import { AppStatusDot } from '../data/AppStatusDot.jsx';

export function AppHudBar({
  level = 'ok',
  sources = [],
  fallbackLabel = 'Carregando',
  dotOnly = false,
  expanded = false,
  onOpen,
  style
}) {
  // Parada mostra a primeira conta; com o ponteiro em cima, todas.
  const visible = expanded ? sources : sources.slice(0, 1);

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Abrir Usage Monitor"
      onClick={onOpen}
      style={{
        display: 'flex',
        flexDirection: 'column',
        maxWidth: 420,
        border: '1px solid var(--border)',
        borderRadius: 'var(--r2)',
        boxShadow: 'var(--shadow-8)',
        background: 'var(--surface)',
        overflow: 'hidden',
        cursor: 'default',
        ...style
      }}
    >
      {dotOnly ? (
        // Recolhida ao ponto: o padding da linha com texto faria um ponto de
        // 6px virar uma janela de 38px.
        <div style={{ display: 'flex', alignItems: 'center', height: 'var(--h-hud)', padding: '0 var(--s2)' }}>
          <AppStatusDot level={level} />
        </div>
      ) : (
        <div style={{ padding: 'var(--s1) 0' }}>
          {visible.length === 0 ? (
            <HudRow>
              <AppStatusIndicator level="off">{fallbackLabel}</AppStatusIndicator>
            </HudRow>
          ) : visible.map((source) => (
            <HudRow key={source.label}>
              <AppStatusIndicator level={source.level}>{source.statusLabel}</AppStatusIndicator>
              <span style={NAME}>{source.label}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
                {(source.quotas || []).map((chip) => (
                  <span key={chip.text} style={{ display: 'flex', alignItems: 'center', gap: 'var(--s1)' }}>
                    <AppStatusDot level={chip.level} />
                    <span style={VALUE}>{chip.text}</span>
                  </span>
                ))}
              </span>
            </HudRow>
          ))}
        </div>
      )}
    </div>
  );
}

const NAME = { flex: 1, minWidth: 0, fontFamily: 'var(--mono)', fontSize: 'var(--t12)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' };
const VALUE = { flex: 'none', fontFamily: 'var(--mono)', fontSize: 'var(--t12)' };

// 20px por linha, e não AppDataRow: aquela primitiva floora em 32px mais
// padding, e seis cotas dariam ~288px de painel -- uma janela, não um HUD.
function HudRow({ children }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s3)', height: 20, padding: '0 var(--s3)' }}>
      {children}
    </div>
  );
}
