import React from 'react';

const LEVELS = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--info)', off: 'var(--muted)' };

// Anel de uso: um arco por cota, concêntricos — o de fora é a primeira cota da
// API (a janela curta). Cota sem projeção tem a trilha tracejada. `active`
// desenha o arco fino de sessão ativa em órbita por fora, sem roubar o miolo.
// O kit é estático: no Compose o arco ativo gira e o de fora pulsa em atenção
// só com a política contínua.
export function AppUsageRing({ arcs = [], size = 28, stroke = 3, gap = 1.5, active = false, label, style }) {
  const rings = arcs.slice(0, 3);
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} overflow="visible" style={{ flex: 'none', overflow: 'visible', ...style }}>
      {rings.map((arc, index) => {
        const r = size / 2 - stroke / 2 - index * (stroke + gap);
        if (r <= 0) return null;
        const c = 2 * Math.PI * r;
        const p = Math.max(0, Math.min(1, arc.fraction || 0));
        return (
          <g key={index} transform={`rotate(-90 ${size / 2} ${size / 2})`}>
            <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--pressed-layer)" strokeWidth={stroke}
              strokeDasharray={arc.forecast === false ? `${stroke} ${stroke * 1.4}` : undefined} />
            <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={LEVELS[arc.level] || LEVELS.off} strokeWidth={stroke}
              strokeLinecap="round" strokeDasharray={`${c * p} ${c}`}
              style={{ transition: 'stroke-dasharray var(--spring-gentle)' }} />
          </g>
        );
      })}
      {active ? (() => {
        const r = size / 2 + gap + (stroke * 0.6) / 2;
        const c = 2 * Math.PI * r;
        return (
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--info)" strokeWidth={stroke * 0.6}
            strokeLinecap="round" strokeDasharray={`${c / 4} ${c}`} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
        );
      })() : null}
    </svg>
  );
}
